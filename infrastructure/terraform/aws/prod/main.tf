
provider "aws" {
  region = "us-east-2"
}

resource "aws_instance" "cledgers" {
  ami = "ami-024e6efaf93d85776"
  instance_type = "t2.micro"
  root_block_device {
    volume_size = 30
  }
  key_name = "frank-key-pair"
  vpc_security_group_ids = [
    "sg-06fa06850ab31614a"
  ]

  tags = {
    Name = "cledgers"
  }
  
}
