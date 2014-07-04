#!/bin/sh

echo "Mapping ports"
for i in {49000..49900}; do
    echo "$i ... "
    VBoxManage modifyvm "boot2docker-vm" --natpf1 delete "tcp-port$i"
    VBoxManage modifyvm "boot2docker-vm" --natpf1 "tcp-port$i,tcp,127.0.0.1,$i,,$i"
    #VBoxManage modifyvm "boot2docker-vm" --natpf1 "udp-port$i,udp,,$i,,$i"
done
echo