# -*- mode: ruby -*-
# vi: set ft=ruby :

#boot this on 
# LINUX: 
# vagrant up --provider=libvirt
# MACOSX,WIN,LINUX:
# vagrant up --provider=virtualbox
# MACOSX: 
# vagrant up --provider=xhyve

Vagrant.configure("2") do |config|

  config.vm.box = "centos/7"


  config.vm.network "private_network", ip: "192.168.33.10"
  
  #config.vm.synced_folder "./", "/data"
  config.vm.synced_folder '.', '/vagrant', nfs: true
  
  ##LINUX: 
  config.vm.provider :libvirt do |libvirt|
    libvirt.cpus = 2
    libvirt.memory = 2048
  end

  ## MACOSX,LINUX,WIN:
  config.vm.provider "virtualbox" do |v|
    v.memory = 2048
    v.cpus = 2
  end

  ## MACOSX: -> note, centos7 image doesn't support xhyve
  # config.vm.provider :xhyve do |xhyve|
  #   xhyve.cpus = 2
  #   xhyve.memory = "2G"
  # end

  config.vm.provision "file", 
    source: "../gerrit/bazel-genfiles/plugins/multi-site/multi-site.jar",
    destination: "$HOME/gerrit/"
  config.vm.provision "file", 
    source: "../gerrit/bazel-bin/release.war",
    destination: "$HOME/gerrit/"

  ## todo enable overlay2 fs for docker
  config.vm.provision "shell", inline: <<-SHELL
    # quick fix to make sure 555 permissions that bazel outputs
    # everything with is overwritable by file provisioner next run
    chmod 775 /home/vagrant/gerrit/*; 

    # install dependencies
    yum clean all && sudo yum update -y
    yum install -y \
      git \
      java-1.8.0-openjdk \
      wget \
      haproxy \
      docker 
    systemctl enable docker
    systemctl start docker

    # install docker-compose, once
    if [ ! -f /usr/local/bin/docker-compose ]; then 
      curl -L "https://github.com/docker/compose/releases/download/1.24.0/docker-compose-$(uname -s)-$(uname -m)" \
        -o /usr/local/bin/docker-compose; 
      chmod +x /usr/local/bin/docker-compose
      ln -s /usr/local/bin/docker-compose /usr/sbin/docker-compose
    fi

  SHELL

  config.vm.provision "shell", 
    inline: "cd /vagrant/ && set -x; setup_local_env/setup.sh \
      --release-war-file /home/vagrant/gerrit/release.war \
      --multisite-lib-file /home/vagrant/gerrit/multi-site.jar \
      --replication-type file"
end
