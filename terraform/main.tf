# Enable required APIs
resource "google_project_service" "container" {
  service            = "container.googleapis.com"
  disable_on_destroy = false
}

resource "google_project_service" "artifactregistry" {
  service            = "artifactregistry.googleapis.com"
  disable_on_destroy = false
}

# Artifact Registry for Docker images
resource "google_artifact_registry_repository" "order_repo" {
  location      = var.region
  repository_id = "order-service"
  description   = "Docker repository for Order Service"
  format        = "DOCKER"

  depends_on = [google_project_service.artifactregistry]
}

# GKE Cluster (Autopilot - simpler and recommended for learning)
resource "google_container_cluster" "primary" {
  name     = var.cluster_name
  location = var.region

  enable_autopilot = true

  # Needed so Terraform can manage the cluster
  deletion_protection = false

  depends_on = [google_project_service.container]
}

# Output important values
output "cluster_name" {
  value = google_container_cluster.primary.name
}

output "cluster_endpoint" {
  value = google_container_cluster.primary.endpoint
}

output "artifact_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/order-service"
}