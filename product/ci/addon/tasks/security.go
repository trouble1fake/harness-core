// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"context"
	"fmt"
	"github.com/wings-software/portal/commons/go/lib/filesystem"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/wings-software/portal/commons/go/lib/exec"
	"github.com/wings-software/portal/commons/go/lib/images"
	"github.com/wings-software/portal/commons/go/lib/utils"
	"github.com/wings-software/portal/product/ci/addon/resolver"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
)

//go:generate mockgen -source security.go -package=tasks -destination mocks/security_mock.go SecurityTask

const (
	defaultSecurityTimeout    int64         = 14400 // 4 hour
	defaultSecurityNumRetries int32         = 1
	securityCmdExitWaitTime   time.Duration = time.Duration(0)
	outputEnvSecuritySuffix   string        = ".out"
)

// SecurityTask represents interface to execute a security step
type SecurityTask interface {
	Run(ctx context.Context) (map[string]string, int32, error)
}

type securityTask struct {
	id                string
	displayName       string
	timeoutSecs       int64
	numRetries        int32
	image             string
	entrypoint        []string
	environment       map[string]string
	tmpFilePath       string
	envVarOutputs     []string
	prevStepOutputs   map[string]*pb.StepOutput
	logMetrics        bool
	log               *zap.SugaredLogger
	addonLogger       *zap.SugaredLogger
	procWriter        io.Writer
	cmdContextFactory exec.CmdContextFactory
	artifactFilePath  string
	fs                filesystem.FileSystem
	reports           []*pb.Report
}

// NewSecurityTask creates a security step executor
func NewSecurityTask(step *pb.UnitStep, prevStepOutputs map[string]*pb.StepOutput, tmpFilePath string,
	log *zap.SugaredLogger, w io.Writer, logMetrics bool, addonLogger *zap.SugaredLogger) SecurityTask {
	r := step.GetSecurity()
	fs := filesystem.NewOSFileSystem(log)
	timeoutSecs := r.GetContext().GetExecutionTimeoutSecs()
	if timeoutSecs == 0 {
		timeoutSecs = defaultSecurityTimeout
	}

	numRetries := r.GetContext().GetNumRetries()
	if numRetries == 0 {
		numRetries = defaultSecurityNumRetries
	}
	return &securityTask{
		id:                step.GetId(),
		displayName:       step.GetDisplayName(),
		image:             r.GetImage(),
		entrypoint:        r.GetEntrypoint(),
		environment:       r.GetEnvironment(),
		envVarOutputs:     r.GetEnvVarOutputs(),
		tmpFilePath:       tmpFilePath,
		reports:           r.GetReports(),
		timeoutSecs:       timeoutSecs,
		numRetries:        numRetries,
		prevStepOutputs:   prevStepOutputs,
		cmdContextFactory: exec.OsCommandContextGracefulWithLog(log),
		logMetrics:        logMetrics,
		log:               log,
		procWriter:        w,
		fs:                fs,
		addonLogger:       addonLogger,
		artifactFilePath:  r.GetArtifactFilePath(),
	}
}

// Executes customer provided security with retries and timeout handling
func (t *securityTask) Run(ctx context.Context) (map[string]string, int32, error) {
	var err error
	var o map[string]string
	for i := int32(1); i <= t.numRetries; i++ {
		if o, err = t.execute(ctx, i); err == nil {
			st := time.Now()
			err = collectTestReports(ctx, t.reports, t.id, t.log)
			if err != nil {
				// If there's an error in collecting reports, we won't retry but
				// the step will be marked as an error
				t.log.Errorw("unable to collect test reports", zap.Error(err))
				return nil, t.numRetries, err
			}
			if len(t.reports) > 0 {
				t.log.Infow(fmt.Sprintf("collected test reports in %s time", time.Since(st)))
			}
			return o, i, nil
		}
	}
	if err != nil {
		// Run step did not execute successfully
		// Try and collect reports, ignore any errors during report collection itself
		errc := collectTestReports(ctx, t.reports, t.id, t.log)
		if errc != nil {
			t.log.Errorw("error while collecting test reports", zap.Error(errc))
		}
		return nil, t.numRetries, err
	}
	return nil, t.numRetries, err
}

// resolveExprInEnv resolves JEXL expressions & env var present in security settings environment variables
func (t *securityTask) resolveExprInEnv(ctx context.Context) (map[string]string, error) {
	envVarMap := getEnvVars()
	for k, v := range t.environment {
		envVarMap[k] = v
	}

	// Resolves secret in environment variables e.g. foo-${ngSecretManager.obtain("secret", 1234)}
	resolvedSecretMap, err := resolver.ResolveSecretInMapValues(envVarMap)
	if err != nil {
		return nil, err
	}

	return resolvedSecretMap, nil
}

func (t *securityTask) execute(ctx context.Context, retryCount int32) (map[string]string, error) {
	start := time.Now()
	ctx, cancel := context.WithTimeout(ctx, time.Second*time.Duration(t.timeoutSecs))
	defer cancel()

	commands, err := t.getEntrypoint(ctx)
	if err != nil {
		logSecurityErr(t.log, "failed to find entrypoint for security", t.id, commands, retryCount, start, err)
		return nil, err
	}

	if len(commands) == 0 {
		err := fmt.Errorf("security entrypoint is empty")
		logSecurityErr(t.log, "entrypoint fetched from remote for security is empty", t.id, commands, retryCount, start, err)
		return nil, err
	}

	envVarsMap, err := t.resolveExprInEnv(ctx)
	if err != nil {
		logSecurityErr(t.log, "failed to evaluate JEXL expression for settings", t.id, commands, retryCount, start, err)
		return nil, err
	}

	cmd := t.cmdContextFactory.CmdContextWithSleep(ctx, securityCmdExitWaitTime, commands[0], commands[1:]...).
		WithStdout(t.procWriter).WithStderr(t.procWriter).WithEnvVarsMap(envVarsMap)
	err = runCmd(ctx, cmd, t.id, commands, retryCount, start, t.logMetrics, t.addonLogger)
	if err != nil {
		return nil, err
	}

	outputFile := filepath.Join(t.tmpFilePath, fmt.Sprintf("%s%s", t.id, outputEnvSecuritySuffix))
	stepOutput := make(map[string]string)
	if len(t.envVarOutputs) != 0 {
		var err error
		outputVars, err := fetchOutputVariables(outputFile, t.fs, t.log)
		if err != nil {
			logSecurityErr(t.log, "error encountered while fetching output of security step", t.id, nil, retryCount, start, err)
			return nil, err
		}

		stepOutput = outputVars
	}

	t.addonLogger.Infow(
		"Successfully executed security",
		"arguments", commands,
		"output", stepOutput,
		"elapsed_time_ms", utils.TimeSince(start),
	)
	return stepOutput, err
}

func (t *securityTask) getEntrypoint(ctx context.Context) ([]string, error) {
	if len(t.entrypoint) != 0 {
		return t.entrypoint, nil
	}

	imageSecret, _ := os.LookupEnv(imageSecretEnv)
	return t.combinedEntrypoint(getImgMetadata(ctx, t.id, t.image, imageSecret, t.log))
}

func (t *securityTask) combinedEntrypoint(ep, cmds []string, err error) ([]string, error) {
	if err != nil {
		return nil, err
	}
	return images.CombinedEntrypoint(ep, cmds), nil
}

func (t *securityTask) readSecurityOutput() (map[string]string, error) {
	return make(map[string]string), nil
}

func logSecurityErr(log *zap.SugaredLogger, errMsg, stepID string, cmds []string, retryCount int32, startTime time.Time, err error) {
	log.Errorw(
		errMsg,
		"retry_count", retryCount,
		"commands", cmds,
		"elapsed_time_ms", utils.TimeSince(startTime),
		zap.Error(err),
	)
}
