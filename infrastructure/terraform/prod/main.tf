
provider "aws" {
  region = "us-east-2"
}

resource "aws_instance" "cledgers" {
  ami = "ami-024e6efaf93d85776"
  instance_type = "t2.micro"
  root_block_device {
    volume_size = 30
  }

  tags = {
    Name = "cledgers"
  }
  
}
