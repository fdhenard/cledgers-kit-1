
terraform {
  required_providers {
    digitalocean = {
      source = "digitalocean/digitalocean"
      version = "~> 2.0"
    }
  }
}

variable "do_token" {}
# variable "pvt_key" {}
variable "do_ssh_key_name" {}
# variable "cledgers_db_pass" {}

provider "digitalocean" {
  token = var.do_token
}

data "digitalocean_ssh_key" "terraform" {
  name = var.do_ssh_key_name
}

data "digitalocean_kubernetes_versions" "do_k8s_versions" {
  # Grab the latest version slug from `doctl kubernetes options versions`
  version_prefix = "1.27."
}

locals {
  region = "nyc1"
}



resource "digitalocean_kubernetes_cluster" "cledgers_k8s" {
  name   = "cledgers"
  region = local.region
  # Grab the latest version slug from `doctl kubernetes options versions`
  # version = "1.27.8-do.0"
  auto_upgrade = true
  version = data.digitalocean_kubernetes_versions.do_k8s_versions.latest_version

  maintenance_policy {
    start_time  = "00:00"
    day         = "sunday"
  }


  node_pool {
    name       = "worker-pool"
    size       = "s-1vcpu-2gb"
    node_count = 1

    # taint {
    #   key    = "workloadKind"
    #   value  = "database"
    #   effect = "NoSchedule"
    # }
  }
}
