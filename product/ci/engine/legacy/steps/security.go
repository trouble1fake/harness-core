// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package steps

import (
	"context"
	"time"

	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	"github.com/pkg/errors"
	"github.com/wings-software/portal/commons/go/lib/utils"
	addonpb "github.com/wings-software/portal/product/ci/addon/proto"
	"github.com/wings-software/portal/product/ci/engine/output"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
)

// go:generate mockgen -source security.go -package=steps -destination mocks/security_mock.go SecurityStep

// SecurityStep represents interface to execute a security step
type SecurityStep interface {
	Run(ctx context.Context) (*output.StepOutput, int32, error)
}

type securityStep struct {
	id            string
	image         string
	step          *pb.UnitStep
	containerPort uint32
	stageOutput   output.StageOutput
	log           *zap.SugaredLogger
}

// NewSecurityStep creates a security step executor
func NewSecurityStep(step *pb.UnitStep, stageOutput output.StageOutput,
	log *zap.SugaredLogger) SecurityStep {
	r := step.GetSecurity()
	return &securityStep{
		id:            step.GetId(),
		image:         r.GetImage(),
		step:          step,
		containerPort: r.GetContainerPort(),
		stageOutput:   stageOutput,
		log:           log,
	}
}

// Executes customer provided security step
func (e *securityStep) Run(ctx context.Context) (*output.StepOutput, int32, error) {
	if err := e.validate(); err != nil {
		e.log.Errorw("failed to validate security step", "step_id", e.id, zap.Error(err))
		return nil, int32(1), err
	}
	return e.execute(ctx)
}

func (e *securityStep) validate() error {
	if e.image == "" {
		err := errors.New("security image is not set")
		return err
	}
	if e.containerPort == 0 {
		err := errors.New("security step container port is not set")
		return err
	}
	return nil
}

func (e *securityStep) execute(ctx context.Context) (*output.StepOutput, int32, error) {
	st := time.Now()

	addonClient, err := newAddonClient(uint(e.containerPort), e.log)
	if err != nil {
		e.log.Errorw("Unable to create CI addon client", "step_id", e.id, "elapsed_time_ms", utils.TimeSince(st), zap.Error(err))
		return nil, int32(1), errors.Wrap(err, "Could not create CI Addon client")
	}
	defer addonClient.CloseConn()

	c := addonClient.Client()
	arg := e.getExecuteStepArg()
	ret, err := c.ExecuteStep(ctx, arg, grpc_retry.WithMax(maxAddonRetries))
	if err != nil {
		e.log.Errorw("Security step RPC failed", "step_id", e.id, "elapsed_time_ms", utils.TimeSince(st), zap.Error(err))
		return nil, int32(1), err
	}
	e.log.Infow("Successfully executed step", "step_id", e.id, "elapsed_time_ms", utils.TimeSince(st))
	stepOutput := &output.StepOutput{}
	stepOutput.Output.Variables = ret.GetOutput()
	return stepOutput, ret.GetNumRetries(), nil
}

func (e *securityStep) getExecuteStepArg() *addonpb.ExecuteStepRequest {
	prevStepOutputs := make(map[string]*pb.StepOutput)
	for stepID, stepOutput := range e.stageOutput {
		if stepOutput != nil {
			prevStepOutputs[stepID] = &pb.StepOutput{Output: stepOutput.Output.Variables}
		}
	}

	return &addonpb.ExecuteStepRequest{
		Step:            e.step,
		PrevStepOutputs: prevStepOutputs,
	}
}
