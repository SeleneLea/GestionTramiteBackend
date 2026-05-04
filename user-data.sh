#!/bin/bash
dnf update -y
dnf install -y java-17-amazon-corretto docker
systemctl enable docker
systemctl start docker
usermod -aG docker ec2-user
ln -s /usr/libexec/docker/cli-plugins/docker-compose /usr/local/bin/docker-compose
mkdir -p /home/ec2-user/tramites
chown -R ec2-user:ec2-user /home/ec2-user/tramites
