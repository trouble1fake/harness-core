// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package steps

import (
	"context"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/pkg/errors"
	"github.com/stretchr/testify/assert"
	"github.com/wings-software/portal/commons/go/lib/logs"
	caddon "github.com/wings-software/portal/product/ci/addon/grpc/client"
	amgrpc "github.com/wings-software/portal/product/ci/addon/grpc/client/mocks"
	addonpb "github.com/wings-software/portal/product/ci/addon/proto"
	"github.com/wings-software/portal/product/ci/engine/output"
	pb "github.com/wings-software/portal/product/ci/engine/proto"
	"go.uber.org/zap"
)

func TestSecurityStepValidate(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	e := securityStep{
		log: log.Sugar(),
	}
	_, _, err := e.Run(ctx)
	assert.NotNil(t, err)

	e = securityStep{
		image: "plugin/sto",
		log:   log.Sugar(),
	}
	_, _, err = e.Run(ctx)
	assert.NotNil(t, err)

	e = securityStep{
		image:         "plugin/sto",
		containerPort: uint32(8000),
		log:           log.Sugar(),
	}
	err = e.validate()
	assert.Nil(t, err)
}

// Client creation failing
func TestSecurityExecuteClientErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Security{
			Security: &pb.SecurityStep{
				Image:         "plugin/sto",
				ContainerPort: port,
			},
		},
	}

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return nil, errors.New("client create error")
	}

	executor := NewSecurityStep(step, nil, log.Sugar())
	_, numRetries, err := executor.Run(ctx)
	assert.NotNil(t, err)
	assert.Equal(t, numRetries, int32(1))
}

// Failed to send GRPC request
func TestSecurityExecuteServerErr(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint32(8000)
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Security{
			Security: &pb.SecurityStep{
				Image:         "plugin/sto",
				ContainerPort: port,
			},
		},
	}

	c := &mockClient{
		response: nil,
		err:      errors.New("server not running"),
	}
	mClient := amgrpc.NewMockAddonClient(ctrl)
	mClient.EXPECT().CloseConn().Return(nil)
	mClient.EXPECT().Client().Return(c)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return mClient, nil
	}

	executor := NewSecurityStep(step, nil, log.Sugar())
	_, numRetries, err := executor.Run(ctx)
	assert.NotNil(t, err)
	assert.Equal(t, numRetries, int32(1))
}

// Success
func TestSecurityExecuteSuccess(t *testing.T) {
	ctrl, ctx := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	port := uint32(8000)
	so := make(map[string]*output.StepOutput)
	so["step1"] = &output.StepOutput{}
	log, _ := logs.GetObservedLogger(zap.InfoLevel)
	step := &pb.UnitStep{
		Id: "test",
		Step: &pb.UnitStep_Security{
			Security: &pb.SecurityStep{
				Image:         "plugin/sto",
				ContainerPort: port,
			},
		},
	}

	numRetries := int32(3)
	c := &mockClient{
		response: &addonpb.ExecuteStepResponse{
			NumRetries: numRetries,
		},
		err: nil,
	}
	mClient := amgrpc.NewMockAddonClient(ctrl)
	mClient.EXPECT().CloseConn().Return(nil)
	mClient.EXPECT().Client().Return(c)

	oldClient := newAddonClient
	defer func() { newAddonClient = oldClient }()
	newAddonClient = func(port uint, log *zap.SugaredLogger) (caddon.AddonClient, error) {
		return mClient, nil
	}

	executor := NewSecurityStep(step, so, log.Sugar())
	_, n, err := executor.Run(ctx)
	assert.Nil(t, err)
	assert.Equal(t, n, numRetries)
}
