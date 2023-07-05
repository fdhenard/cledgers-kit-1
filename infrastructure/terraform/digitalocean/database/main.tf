
resource "digitalocean_database_cluster" "cledgers_db" {
  name       = "cledgers-non-prod"
  engine     = "pg"
  version    = "15"
  size       = "db-s-1vcpu-1gb"
  region     = local.region
  node_count = 1
  project_id = "cledgers"
  # password   = var.cledgers_db_pass
}

# resource "digitalocean_database_db" "cledgers" {
#   cluster_id = digitalocean_database_cluster.cledgers_db.id
#   name       = "cledgers_non_prod"
# }

# resource "digitalocean_database_user" "user-example" {
#   cluster_id = digitalocean_database_cluster.cledgers_db.id
#   name       = "foobar"
# }
