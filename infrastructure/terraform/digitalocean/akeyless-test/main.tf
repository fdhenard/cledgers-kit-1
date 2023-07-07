terraform {
  required_providers {
    akeyless = {
      source = "akeyless-community/akeyless"
      version = "1.3.1"
    }
  }
}

variable akeyless_access_id {}
variable akeyless_access_key {}

provider "akeyless" {
  api_gateway_address = "https://api.akeyless.io"

  api_key_login {
    access_id  = var.akeyless_access_id
    access_key = var.akeyless_acesss_token
  }
}

# resource "akeyless_auth_method_api_key" "api_key" {
#   name = "api_key"

  
#   # path = "terraform-tests/auth-method-api-key-demo"
#   # api_key {
#   # }

# }

# resource "akeyless_static_secret" "secret" {
#   path = "terraform-tests/secret"
#   value = "this value was set from terraform"
# }

data "akeyless_secret" "secret" {
  # depends_on = [
  #   akeyless_static_secret.secret
  # ]
  # path = "terraform-tests/secret"
  # path = "Personal/testing"
  path = "location/test2"
}

output "secret" {
  value     = data.akeyless_secret.secret
  sensitive = true
  # sensitive = false
}

# output "auth_method" {
#   value     = akeyless_auth_method_api_key.api_key
#   sensitive = true
# }
