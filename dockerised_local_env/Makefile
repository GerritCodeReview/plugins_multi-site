GERRIT_JOB=Gerrit-bazel-stable-2.16
BUILD_NUM=259
GERRIT_1_PLUGINS_DIRECTORY=./gerrit-1/plugins
GERRIT_2_PLUGINS_DIRECTORY=./gerrit-2/plugins
GERRIT_1_BIN_DIRECTORY=./gerrit-1/bin
GERRIT_2_BIN_DIRECTORY=./gerrit-2/bin
CI_URL=https://gerrit-ci.gerritforge.com/job
MYDIR=$(shell basename $(shell pwd))
WGET=wget -N -q

all: download build

download: gerrit plugin_websession_flatfile \
	plugin_healthcheck
	# plugin_multi_site


gerrit:
	-mkdir -p $(GERRIT_1_PLUGINS_DIRECTORY)
	-mkdir -p $(GERRIT_2_PLUGINS_DIRECTORY)
	$(WGET) $(CI_URL)/$(GERRIT_JOB)/lastSuccessfulBuild/artifact/gerrit/bazel-bin/release.war -P $(GERRIT_1_BIN_DIRECTORY)
	cp $(GERRIT_1_BIN_DIRECTORY)/*.war $(GERRIT_2_BIN_DIRECTORY)

plugin_websession_flatfile:
	$(WGET) $(CI_URL)/plugin-websession-flatfile-bazel-master-stable-2.16/lastSuccessfulBuild/artifact/bazel-genfiles/plugins/websession-flatfile/websession-flatfile.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/websession-flatfile.jar $(GERRIT_2_PLUGINS_DIRECTORY)/websession-flatfile.jar

plugin_multi_site:
	$(WGET) $(CI_URL)/plugin-multi-site-bazel-gerritforge-stable-2.16/lastSuccessfulBuild/artifact/bazel-genfiles/plugins/multi-site/multi-site.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/multi-site.jar $(GERRIT_2_PLUGINS_DIRECTORY)/multi-site.jar

plugin_healthcheck:
	$(WGET) $(CI_URL)/plugin-healthcheck-bazel-stable-2.16/lastSuccessfulBuild/artifact/bazel-genfiles/plugins/healthcheck/healthcheck.jar -P $(GERRIT_1_PLUGINS_DIRECTORY)
	cp $(GERRIT_1_PLUGINS_DIRECTORY)/healthcheck.jar $(GERRIT_2_PLUGINS_DIRECTORY)/healthcheck.jar

build:
	docker build -t $(MYDIR) ./gerrit-1
	docker build -t $(MYDIR) ./gerrit-2
clean_gerrit:
	rm -fr gerrit-1/db/ gerrit-1/data/ gerrit-1/cache/ gerrit-1/db/ gerrit-1/git/ gerrit-1/indexes/
	rm -fr gerrit-2/db/ gerrit-2/data/ gerrit-2/cache/ gerrit-2/db/ gerrit-2/git/ gerrit-2/indexes/

init_gerrit:
	docker-compose down && docker-compose build gerrit-1 && docker-compose build gerrit-2 && docker-compose up -d gerrit-1 && docker-compose up -d gerrit-2

init_all: clean_gerrit
		docker-compose down && docker-compose build && docker-compose up -d
