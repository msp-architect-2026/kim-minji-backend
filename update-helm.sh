#!/bin/sh
apk add --no-cache git
git config --global user.email "gitlab-ci@gitlab.local"
git config --global user.name "GitLab CI"
git config --global http.sslVerify false
git clone http://gitlab-ci-token:${CI_JOB_TOKEN}@gitlab.local:8929/root/kim-minji-helm.git
cd kim-minji-helm
sed -i "s/tag:.*/tag: \"${CI_COMMIT_SHORT_SHA}\"/" backend/values.yaml
git add backend/values.yaml
git commit -m "ci update backend tag ${CI_COMMIT_SHORT_SHA}"
git push origin main