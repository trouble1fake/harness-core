<#import "upgrader-role.ftl" as upgraderRole>
<#macro cronjob fullDelegateName=delegateName>
<#assign upgraderSaName = "upgrader-cronjob-sa">
<@upgraderRole.cronJobRole upgraderSaName />

---

apiVersion: batch/v1beta1
kind: CronJob
metadata:
  labels:
    harness.io/name: ${fullDelegateName}-upgrader-job
  name: ${fullDelegateName}-upgrader-job
  namespace: ${delegateNamespace}
spec:
  schedule: "0 */1 * * *"
  concurrencyPolicy: Forbid
  startingDeadlineSeconds: 20
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: ${upgraderSaName}
          containers:
          - image: ${upgraderDockerImage}
            name: upgrader
            imagePullPolicy: Always
            env:
            - name: POD_NAMESPACE
              valueFrom:
                fieldRef:
                  fieldPath: metadata.namespace
            - name: ACCOUNT_ID
              value: ${accountId}
            - name: ACCOUNT_SECRET
              value: ${accountSecret}
            - name: MANAGER_HOST_AND_PORT
              value: ${managerHostAndPort}
            - name: DELEGATE_NAME
              value: ${fullDelegateName}
          restartPolicy: Never
</#macro>