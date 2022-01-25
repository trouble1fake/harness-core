<#macro accountToken secret>
apiVersion: v1
kind: Secret
metadata:
  name: ${accountTokenName}
  namespace: ${delegateNamespace}
type: Opaque
data:
  ACCOUNT_SECRET: "${secret}"
</#macro>