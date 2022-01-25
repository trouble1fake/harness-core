<#import "common/delegate-environment.ftl" as delegateEnvironment>
<#import "common/delegate-role.ftl" as delegateRole>
<#import "common/delegate-service.ftl" as delegateService>
<#import "common/upgrader.ftl" as upgrader>
<#import "common/secret.ftl" as secret>
<#global accountTokenName=delegateName + "-account-token">
apiVersion: v1
kind: Namespace
metadata:
  name: ${delegateNamespace}

---

<#if enableCE == "true">
    <@delegateRole.cgCe />
<#else>
    <@delegateRole.cg />
</#if>

---

<@secret.accountToken accountSecret/>

---

# If delegate needs to use a proxy, please follow instructions available in the documentation
# https://docs.harness.io/article/pfim3oig7o-configure-delegate-proxy-settings

apiVersion: apps/v1
kind: Deployment
metadata:
  labels:
    harness.io/name: ${delegateName}
  # Name must contain the six letter account identifier: ${kubernetesAccountLabel}
  name: ${delegateName}-${kubernetesAccountLabel}
  namespace: ${delegateNamespace}
spec:
  replicas: 1
  selector:
    matchLabels:
      harness.io/name: ${delegateName}
  template:
    metadata:
      labels:
        harness.io/name: ${delegateName}
    spec:
      terminationGracePeriodSeconds: 600
      restartPolicy: Always
      containers:
      - image: ${delegateDockerImage}
        imagePullPolicy: Always
        name: delegate
        <#if ciEnabled == "true">
        ports:
          - containerPort: ${delegateGrpcServicePort}
        </#if>
        resources:
          limits:
            cpu: "${delegateCpu}"
            memory: "${delegateRam}Gi"
        livenessProbe:
          httpGet:
            path: /api/health
            port: 3460
            scheme: HTTP
          initialDelaySeconds: 60
          periodSeconds: 10
          failureThreshold: 2
        envFrom:
        - secretRef:
            name: ${accountTokenName}
        env:
<@delegateEnvironment.common />
<@delegateEnvironment.cgSpecific />
<@delegateEnvironment.immutable />

<#if ciEnabled == "true">
---

    <@delegateService.cg />
</#if>

---

<@upgrader.cronjob fullDelegateName=delegateName + "-" + kubernetesAccountLabel/>
