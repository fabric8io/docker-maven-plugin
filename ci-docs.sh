echo ===========================================
echo Deploying docker-maven-plugin documentation
echo ===========================================

cd doc/manual && \
npm install -g gitbook-cli && \
git clone -b gh-pages git@github.com:rhuss/docker-maven-plugin.git gh-pages && \
mkdir -p _book && \
gitbook install .  && \
gitbook build . && \
cp -rv _book/* gh-pages/ && \
cd gh-pages && \
git add * && \
git commit -m "generated documentation" && \
git push origin gh-pages && \
cd .. && \
rm -rf gh-pages
       
