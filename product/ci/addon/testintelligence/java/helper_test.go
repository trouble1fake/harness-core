// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package java

import (
	"context"
	"os"
	"testing"

	"github.com/golang/mock/gomock"
	"github.com/stretchr/testify/assert"
	"github.com/wings-software/portal/commons/go/lib/filesystem"
	"github.com/wings-software/portal/commons/go/lib/logs"
	"go.uber.org/zap"
)

const (
	pkgDetectTestdata  = "testdata/pkg_detection"
	testDataWithTag    = "testdata/harness_tag_detection/with_tag"
	testDataWithoutTag = "testdata/harness_tag_detection/without_tag"
)

func TestDetectJavaPkgs(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("could not get working directory: %s", err)
	}
	wd = wd + pkgDetectTestdata

	oldGetWorkspace := getWorkspace
	defer func() {
		getWorkspace = oldGetWorkspace
	}()
	getWorkspace = func() (string, error) {
		return pkgDetectTestdata, nil
	}

	l, err := DetectPkgs(log.Sugar(), filesystem.NewOSFileSystem(log.Sugar()))
	assert.Contains(t, l, "com.google.test.test")
	assert.Contains(t, l, "xyz")
	assert.Contains(t, l, "test1.test1")
	assert.Len(t, l, 3)
	assert.Nil(t, err)
}

func TestDetectHarnessTag(t *testing.T) {
	ctrl, _ := gomock.WithContext(context.Background(), t)
	defer ctrl.Finish()

	log, _ := logs.GetObservedLogger(zap.InfoLevel)

	wd, err := os.Getwd()
	if err != nil {
		t.Fatalf("could not get working directory: %s", err)
	}
	wd = wd + testDataWithTag

	getWorkspace = func() (string, error) {
		return testDataWithTag, nil
	}

	ret, err := DetectHarnessTag(log.Sugar(), filesystem.NewOSFileSystem(log.Sugar()))
	assert.Equal(t, ret, true)
	assert.Nil(t, err)

	// Test without harness tag
	wd, err = os.Getwd()
	if err != nil {
		t.Fatalf("could not get working directory: %s", err)
	}
	wd = wd + testDataWithoutTag

	oldGetWorkspace := getWorkspace
	defer func() {
		getWorkspace = oldGetWorkspace
	}()
	getWorkspace = func() (string, error) {
		return testDataWithoutTag, nil
	}

	ret, err = DetectHarnessTag(log.Sugar(), filesystem.NewOSFileSystem(log.Sugar()))
	assert.Equal(t, ret, false)
	assert.Nil(t, err)
}
