    minikube image load todoist-sync

## Update on kubernetes

1. Push image to the registry

    docker build --platform linux/amd64 -t registry.jetbrains.team/p/td-sync/containers/td-sync:latest .
    docker push registry.jetbrains.team/p/td-sync/containers/td-sync:latest

2. Update deployment
   you could simply delete the pod and let it be recreated by the deployment

3. Apply dashboard update CronJob

    kubectl apply -f kubernetes/todoist-sync-dashboard-update-cronjob.yaml

The CronJob runs the CLI command `update-dashboards-on-server`.
Set interval via `.spec.schedule` and make sure `todoist-sync-secrets` contains `YOUTRACK_TOKEN`.
