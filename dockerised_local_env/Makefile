GERRIT_JOB=Gerrit-bazel-stable-2.16
BUILD_NUM=377
GERRIT_1_BIN_DIRECTORY=./gerrit-1/bin
GERRIT_2_BIN_DIRECTORY=./gerrit-2/bin
GERRIT_1_LIB_DIRECTORY=./gerrit-1/lib
GERRIT_2_LIB_DIRECTORY=./gerrit-2/lib
GERRIT_1_ETC_DIRECTORY=./gerrit-1/etc
GERRIT_2_ETC_DIRECTORY=./gerrit-2/etc
GERRIT_1_PLUGINS_DIRECTORY=./gerrit-1/plugins
GERRIT_2_PLUGINS_DIRECTORY=./gerrit-2/plugins
CORE_PLUGINS=replication
CI_URL=https://gerrit-ci.gerritforge.com/job
MYDIR=$(shell basename $(shell pwd))
WGET=wget -N -q

all: prepare download build

prepare:
	-mkdir -p $(GERRIT_1_PLUGINS_DIRECTORY) $(GERRIT_2_PLUGINS_DIRECTORY) $(GERRIT_1_BIN_DIRECTORY) $(GERRIT_2_BIN_DIRECTORY) $(GERRIT_1_ETC_DIRECTORY) $(GERRIT_2_ETC_DIRECTORY) $(GERRIT_1_LIB_DIRECTORY) $(GERRIT_2_LIB_DIRECTORY)

download: gerrit plugin_websession_flatfile \
	plugin_healthcheck \
	plugin_delete_project \
	plugin_multi_site


gerrit: prepare
	$(WGET) $(CI_URL)/$(GERRIT_JOB)/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war -P $(GERRIT_1_BIN_DIRECTORY)
	cp $(GERRIT_1_BIN_DIRECTORY)/*.war $(GERRIT_2_BIN_DIRECTORY)
	for plugin in $(CORE_PLUGINS); do $(WGET) $(CI_URL)/$(GERRIT_JOB)/lastSuccessfulBuild/artifact/gerrit/bazel-bin/plugins/$$plugin/$$plugin.jar -P $(GERRIT_1_PLUGINS_DIRECTORY); done
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/*.jar $(GERRIT_2_PLUGINS_DIRECTORY)

plugin_websession_flatfile: prepare
	$(WGET) $(CI_URL)/plugin-websession-flatfile-bazel-master-stable-2.16/lastSuccessfulBuild/artifact/bazel-bin/plugins/websession-flatfile/websession-flatfile.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/websession-flatfile.jar $(GERRIT_2_PLUGINS_DIRECTORY)/websession-flatfile.jar

plugin_multi_site: prepare
	$(WGET) $(CI_URL)/plugin-multi-site-bazel-stable-2.16/lastSuccessfulBuild/artifact/bazel-bin/plugins/multi-site/multi-site.jar -P $(GERRIT_1_LIB_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/replication.jar $(GERRIT_1_LIB_DIRECTORY)
	cp $(GERRIT_1_LIB_DIRECTORY)/*.jar $(GERRIT_2_LIB_DIRECTORY)

plugin_healthcheck: prepare
	$(WGET) $(CI_URL)/plugin-healthcheck-bazel-stable-2.16/lastSuccessfulBuild/artifact/bazel-bin/plugins/healthcheck/healthcheck.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/healthcheck.jar $(GERRIT_2_PLUGINS_DIRECTORY)/healthcheck.jar

plugin_delete_project: prepare
	$(WGET) $(CI_URL)/plugin-delete-project-bazel-stable-2.16/lastSuccessfulBuild/artifact/bazel-bin/plugins/delete-project/delete-project.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/delete-project.jar $(GERRIT_2_PLUGINS_DIRECTORY)/delete-project.jar

build:
	docker build -t $(MYDIR) ./gerrit-1
	docker build -t $(MYDIR) ./gerrit-2

clean_gerrit: prepare
	-rm -fr gerrit-{1,2}/{db,data,cache,db,git,index,etc,bin,tmp,plugins,lib}/*
	export GERRIT_REPLICATION_INSTANCE=gerrit-2; cat ./gerrit-common/replication.config.template | envsubst '$${GERRIT_REPLICATION_INSTANCE}' > ./gerrit-1/etc/replication.config
	export GERRIT_REPLICATION_INSTANCE=gerrit-1; cat ./gerrit-common/replication.config.template | envsubst '$${GERRIT_REPLICATION_INSTANCE}' > ./gerrit-2/etc/replication.config
	cp ./gerrit-common/*.config ./gerrit-1/etc
	cp ./gerrit-common/*.config ./gerrit-2/etc
	cp ./gerrit-common/git-daemon.sh ./gerrit-1/bin
	cp ./gerrit-common/git-daemon.sh ./gerrit-2/bin

init_gerrit:
	docker-compose down && docker-compose build gerrit-1 && docker-compose build gerrit-2 && docker-compose up -d gerrit-1 && docker-compose up -d gerrit-2

init_all: clean_gerrit download
		docker-compose down && docker-compose build && INIT=1 docker-compose up -d
restart_gerrit_1:
		cp ./gerrit-common/*.db ./gerrit-1/db
		-docker-compose kill gerrit-1
		sleep 3; INIT=0 docker-compose up -d gerrit-1
restart_gerrit_2:
		cp ./gerrit-common/*.db ./gerrit-2/db
		-docker-compose kill gerrit-2
		sleep 3; INIT=0 docker-compose up -d gerrit-2
