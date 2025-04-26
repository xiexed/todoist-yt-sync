
    minikube image load todoist-sync

## Update on kubernetes

1. Push image to the registry

    docker build --platform linux/amd64 -t registry.jetbrains.team/p/td-sync/containers/td-sync:latest .
    docker push registry.jetbrains.team/p/td-sync/containers/td-sync:latest

2. Update deployment
   you could simply delete the pod and let it be recreated by the deployment