<#macro ng>
apiVersion: v1
kind: Service
metadata:
  name: delegate-service
  namespace: ${delegateNamespace}
<#if isImmutable == "true">
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "3460"
    prometheus.io/path: "/api/metrics"
</#if>
spec:
  type: ClusterIP
  selector:
    harness.io/name: ${delegateName}
  ports:
    - port: ${delegateGrpcServicePort}
</#macro>
<#macro cg>
apiVersion: v1
kind: Service
metadata:
  name: delegate-service
  namespace: harness-delegate
<#if isImmutable == "true">
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "3460"
    prometheus.io/path: "/api/metrics"
</#if>
spec:
  type: ClusterIP
  selector:
    harness.io/app: harness-delegate
    harness.io/account: ${kubernetesAccountLabel}
    harness.io/name: ${delegateName}
  ports:
    - port: ${delegateGrpcServicePort}
</#macro>
