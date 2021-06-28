
FROM node:10-stretch AS build

RUN npm i -g yarn node-gyp --force
RUN mkdir /usr/app

ADD  . /usr/app/repo/frontend-web

WORKDIR /usr/app/repo/frontend-web
RUN NODE_ENV=development yarn --frozen-lockfile
RUN NODE_ENV=production yarn build:docker


FROM httpd as runtime

COPY --from=build /usr/app/repo/frontend-web/dist/ /usr/local/apache2/htdocs/
COPY ./conf/apache-forpdi.conf $HTTPD_PREFIX/conf/extra/forpdi.conf
COPY ./conf/apache-httpd.conf $HTTPD_PREFIX/conf/httpd.conf

EXPOSE 80
