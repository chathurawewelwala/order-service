variable "project_id" {
  description = "GCP Project ID"
  type        = string
  default     = "order-service-gke-demo"
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "asia-south1"
}

variable "zone" {
  description = "GCP zone"
  type        = string
  default     = "asia-south1-a"
}

variable "cluster_name" {
  description = "GKE cluster name"
  type        = string
  default     = "order-cluster"
}