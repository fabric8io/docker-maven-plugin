echo ===========================================
echo Deploying docker-maven-plugin documentation
echo ===========================================

cd docs && \
npm install -g gitbook-cli && \
git clone -b gh-pages git@github.com:rhuss/docker-maven-plugin.git && \
mkdir -p _book && \
gitbook install .  && \
gitbook build . && \
cp -rv _book/* gh-pages/ && \
git add * && \
git commit -m "generated documentation" && \
git push origin gh-pages
