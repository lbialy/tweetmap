###Twitter stream map

####A reactive Vert.x + Rx + Angular.js demo application

How to run:
- get JDK 1.8
- get maven
- put maven and java in path
- clone this repo
- obtain Twitter API credentials 
- create run.sh in file with those contents and replace placeholders:

```bash
#!/bin/bash
rm -fr target && mvn package && env $envvars java -jar target/tweetmap-1.0-SNAPSHOT-fat.jar \
-Dhttp.port=80 \
-Dtwitter4j.debug=false \
-Dtwitter4j.oauth.consumerKey=CONSUMER_KEY_GOES_HERE \
-Dtwitter4j.oauth.consumerSecret=CONSUMER_SECRET_GOES_HERE \
-Dtwitter4j.oauth.accessToken=ACCESS_TOKEN_GOES_HERE \
-Dtwitter4j.oauth.accessTokenSecret=ACCESS_TOKEN_SECRET_GOES_HERE
```
- `chmod +x run.sh` 

Execute `./run.sh`. App will run on configured port, default 80 or 8080 if not provided in run.sh.