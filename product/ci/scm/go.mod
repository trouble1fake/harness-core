module github.com/wings-software/portal/product/ci/scm

go 1.14

replace github.com/wings-software/portal/commons/go/lib => ../../../commons/go/lib

replace github.com/wings-software/portal/product/log-service => ../../../product/log-service

require (
	github.com/alexflint/go-arg v1.3.0
	github.com/drone/go-scm v1.18.0
	github.com/drone/go-scm-codecommit v0.0.0-20210315104920-2d8b9dc5ed8a
	github.com/golang/mock v1.6.0
	github.com/golang/protobuf v1.5.2
	github.com/grpc-ecosystem/go-grpc-middleware v1.2.1
	github.com/nbio/st v0.0.0-20140626010706-e9e8d9816f32 // indirect
	github.com/stretchr/testify v1.7.0
	github.com/wings-software/portal/commons/go/lib v0.0.0-00010101000000-000000000000
	github.com/wings-software/portal/product/log-service v0.0.0-20210305084455-298bbd5bd1fd // indirect
	go.uber.org/zap v1.15.0
	golang.org/x/net v0.0.0-20220114011407-0dd24b26b47d
	google.golang.org/grpc v1.43.0
	google.golang.org/protobuf v1.27.1
	mvdan.cc/sh/v3 v3.2.4 // indirect
)
