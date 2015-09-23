echo ===========================================
echo Deploying docker-maven-plugin documentation
echo ===========================================

cd doc/manual && \
npm install -g gitbook-cli && \
mkdir -p _book && \
gitbook install .  && \
gitbook build . && \
git clone -b gh-pages git@github.com:rhuss/docker-maven-plugin.git gh-pages && \
cp -rv _book/* gh-pages/ && \
cd gh-pages && \
git add --ignore-errors * && \
git commit -m "generated documentation" && \
git push origin gh-pages && \
cd .. && \
rm -r gh-pages _book node-modules
       
