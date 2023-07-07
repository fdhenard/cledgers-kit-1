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
variable "my_public_ip" {}

provider "digitalocean" {
  token = var.do_token
}

data "digitalocean_ssh_key" "terraform" {
  name = var.do_ssh_key_name
}

locals {
  region = "nyc1"
}




resource "digitalocean_database_cluster" "cledgers" {
  name       = "cledgers-non-prod"
  engine     = "pg"
  version    = "15"
  size       = "db-s-1vcpu-1gb"
  region     = local.region
  node_count = 1
  project_id = "cledgers"
  # password   = var.cledgers_db_pass
}

resource "digitalocean_database_db" "cledgers_non_prod" {
  cluster_id = digitalocean_database_cluster.cledgers.id
  name       = "cledgers_non_prod"
}

resource "digitalocean_database_user" "cledgers" {
  cluster_id = digitalocean_database_cluster.cledgers.id
  name       = "cledgers"
}

resource "digitalocean_database_firewall" "cledgers_fw" {
  cluster_id = digitalocean_database_cluster.cledgers.id

  rule {
    type  = "ip_addr"
    value = var.my_public_ip
  }

  # rule {
  #   type  = "k8s"
  #   value = digitalocean_kubernetes_cluster.cledgers_k8s.id
  # }
}
