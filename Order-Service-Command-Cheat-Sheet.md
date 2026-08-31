# Order Service – Complete Command Cheat Sheet

A practical reference of all important commands used in the Order Service project (local Docker → Kubernetes → GKE), with explanations of what each part of the command means.

---

## 1. Docker & Docker Compose

| Command | Meaning |
|-------|--------|
| `docker compose up -d` | Start all services defined in `docker-compose.yml` in **detached** mode (runs in background) |
| `docker compose down` | Stop and remove containers created by `docker compose up` |
| `docker compose down -v` | Same as above + **delete volumes** (database data is lost) |
| `docker compose ps` | Show status of containers managed by Compose |
| `docker compose logs -f order-service` | Follow logs of the `order-service` container |
| `docker build --platform linux/amd64 -t IMAGE .` | Build image for `linux/amd64` architecture (required for GKE) |
| `docker push IMAGE` | Push the image to the registry |
| `docker images` | List local Docker images |
| `docker exec -it order-postgres psql -U orderuser -d orders` | Open interactive terminal inside Postgres container and connect to DB |

**Breakdown example:**
```bash
docker exec -it order-postgres psql -U orderuser -d orders
```
- `docker exec` → run a command inside a running container  
- `-it` → interactive + allocate a terminal  
- `order-postgres` → container name  
- `psql` → PostgreSQL client  
- `-U orderuser` → username  
- `-d orders` → database name  

---

## 2. Kubernetes – Basic

| Command | Meaning |
|-------|--------|
| `kubectl get nodes` | List cluster nodes |
| `kubectl get pods -n orders` | List pods in namespace `orders` |
| `kubectl get all -n orders` | List pods, services, deployments, etc. in the namespace |
| `kubectl get svc -n orders` | List services |
| `kubectl get ingress -n orders` | List Ingress resources |
| `kubectl get hpa -n orders` | List Horizontal Pod Autoscalers |
| `kubectl get pdb -n orders` | List PodDisruptionBudgets |
| `kubectl get networkpolicy -n orders` | List NetworkPolicies |
| `kubectl get podmonitoring -n orders` | List Google Managed Prometheus PodMonitoring |

**Useful flags:**
- `-n orders` → namespace
- `-w` → watch (live updates)
- `-o yaml` → output in YAML

---

## 3. Kubernetes – Debugging & Logs

| Command | Meaning |
|-------|--------|
| `kubectl logs <pod-name> -n orders` | Show logs of a pod |
| `kubectl logs <pod-name> -n orders --previous` | Show logs of the **previous** crashed container |
| `kubectl logs -l app.kubernetes.io/name=order-service -n orders --tail=100 -f` | Follow logs of all order-service pods |
| `kubectl describe pod <pod-name> -n orders` | Detailed information + events of a pod |
| `kubectl describe ingress order-service -n orders` | Details of the Ingress (including backend health) |
| `kubectl exec -it deploy/order-service -n orders -- env` | Run `env` command inside a pod of the deployment |
| `kubectl top pods -n orders` | Show real-time CPU / Memory usage of pods |
| `kubectl top nodes` | Show real-time CPU / Memory usage of nodes |
| `kubectl exec -it <pod-name> -n orders -- bash` | Get a shell inside the pod (if it’s still running) |
| `jcmd 1 GC.run_finalization` | Inside the pod (if jmap/jcmd available) |
| `jcmd 1 VM.native_memory` | Inside the pod (if jmap/jcmd available) |

---

## 4. Kubernetes – Deployments & Rollouts

| Command | Meaning |
|-------|--------|
| `kubectl rollout status deployment/order-service -n orders` | Wait and show status of a rollout |
| `kubectl rollout restart deployment/order-service -n orders` | Restart all pods of the deployment (rolling restart) |
| `kubectl scale deployment order-service --replicas=3 -n orders` | Manually scale to 3 replicas |
| `kubectl delete pod <pod-name> -n orders` | Delete a pod (Deployment will recreate it) |

---

## 5. Helm

| Command | Meaning |
|-------|--------|
| `helm create order-service` | Create a new Helm chart |
| `helm install order-service ./order-service -n orders` | Install the chart for the first time |
| `helm upgrade order-service ./order-service -n orders` | Upgrade an existing release |
| `helm upgrade --install order-service ./order-service -n orders` | Install if not exists, otherwise upgrade |
| `helm uninstall order-service -n orders` | Remove the release |
| `helm list -n orders` | List Helm releases in the namespace |

---

## 6. GKE & gcloud

| Command | Meaning |
|-------|--------|
| `gcloud container clusters get-credentials order-cluster --region asia-south1` | Configure `kubectl` to talk to the GKE cluster |
| `gcloud container clusters list` | List GKE clusters |
| `gcloud container clusters describe order-cluster --region asia-south1` | Detailed info about the cluster |
| `gcloud auth configure-docker asia-south1-docker.pkg.dev` | Authenticate Docker to Artifact Registry |
| `gcloud secrets create ...` | Create a secret in Secret Manager |
| `gcloud projects describe order-service-gke-demo --format="value(projectNumber)"` | Get the project number |

---

## 7. Port Forwarding & Access

| Command | Meaning |
|-------|--------|
| `kubectl port-forward svc/order-service 8080:8080 -n orders` | Forward local port 8080 to the service port 8080 |
| `curl http://localhost:8080/actuator/health` | Call the app via port-forward |
| `curl http://8.232.75.51/actuator/health` | Call the app via public Ingress IP |

---

## 8. Terraform

| Command | Meaning |
|-------|--------|
| `terraform init` | Download providers and initialize |
| `terraform plan` | Show what will be created/changed |
| `terraform apply` | Apply the changes (create GKE cluster, etc.) |
| `terraform destroy` | Destroy all resources created by Terraform |
| `terraform output` | Show output values |

---

## 9. Useful One-liners

```powershell
# Watch pods live
kubectl get pods -n orders -w

# Follow logs of all order-service pods
kubectl logs -l app.kubernetes.io/name=order-service -n orders -f --tail=50

# Restart application
kubectl rollout restart deployment/order-service -n orders

# Check HPA status live
kubectl get hpa -n orders -w

# Get public IP of Ingress
kubectl get ingress -n orders -o jsonpath='{.items[0].status.loadBalancer.ingress[0].ip}'
```

---

## 10. PowerShell-specific Tips

In PowerShell, `curl` is an alias for `Invoke-WebRequest`.  
Prefer these:

```powershell
# Health check
Invoke-RestMethod -Uri http://8.232.75.51/actuator/health

# Create order
$body = @{
    customerId  = "test-user"
    productCode = "PROD-001"
    quantity    = 1
    totalAmount = 99.99
} | ConvertTo-Json

Invoke-RestMethod -Uri http://8.232.75.51/api/v1/orders -Method POST -Body $body -ContentType "application/json"
```

---

**Project:** Order Service on GKE  
**Stack:** Java 26 • Spring Boot 4 • PostgreSQL • Redis • Helm • GKE Autopilot • GitHub Actions
