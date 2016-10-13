// this actually works in Chrome and Firefox, transpiled to ES5 anyway -> app.js

((angular, Rx) => {

    const keywordsMessage = keywordsString => JSON.stringify({keywords: keywordsString.split(' ')});

    const tweetmap = angular.module('tweetmap', ['rx', 'ngSanitize', 'ngMap']);

    const tCo = 'https://t.co/';

    const maxTweets = 50;

    /**
     *  addTweet :: Array => Tweet => void
     */
    const addTweet = tweetCollection => tweet => { // currying, yeah!
        if (tweetCollection.length >= maxTweets) {
            tweetCollection.pop();
            tweetCollection.unshift(tweet);
        } else {
            tweetCollection.unshift(tweet);
        }
    };

    tweetmap.service('DataStream', function () {
        const socket = Rx.DOM.fromWebSocket('ws://' + window.location.host + '/ws', null);

        return {
            stream: socket.map(msg => JSON.parse(msg.data)).share(),
            socket: socket
        };
    });

    tweetmap.component('tweetMap', {
        controller: function ($scope, $element, DataStream) {
            const ctrl = this;

            ctrl.tweets = [];

            const addTweetWithGeo = addTweet(ctrl.tweets);

            DataStream.stream
                .filter(tweet => null !== tweet.maybeGeoLocation)
                .subscribe(
                    tweetWithGeo => {
                        addTweetWithGeo(tweetWithGeo);
                        $scope.$apply();
                    },
                    e => console.error('error: %s', e),
                    () => console.info('socket closed')
                );
        },
        templateUrl: 'templates/map.html'
    });

    tweetmap.component('filterInput', {
        controller: function ($scope, $element, DataStream) {
            Rx.Observable.fromEvent($element.find('input'), 'keyup')
                .map(e => e.target.value)
                .debounce(700)
                .map(keyword => keyword.trim())
                .filter(keyword => keyword.length > 0)
                .distinctUntilChanged()
                .forEach(keyword => DataStream.socket.onNext(keywordsMessage(keyword)));

            Rx.Observable.fromEvent($element.find('button'), 'click')
                .forEach(() => {
                    $element.find('input').val('');
                    DataStream.socket.onNext(keywordsMessage(''));
                });
        },
        templateUrl: 'templates/filterInput.html'
    });

    tweetmap.component('tweets', {
        controller: function ($scope, DataStream) {
            const ctrl = this;

            ctrl.tweets = [];

            const addTweetToList = addTweet(ctrl.tweets);

            DataStream.stream.subscribe(
                tweet => {
                    addTweetToList(tweet);
                    $scope.$apply();
                },
                e => console.error('error: %s', e),
                () => console.info('socket closed')
            );
        },
        templateUrl: 'templates/tweets.html'
    });

    tweetmap.component('tweet', {
        controller: function () {
            const ctrl = this;

            const escapeRegex = string => string.replace(/[\[\](){}?*+\^$\\.|\-]/g, "\\$&");

            const trim = (string, chars) => {
                const characters = escapeRegex(chars);
                return string.replace(new RegExp("^[" + characters + "]+|[" + characters + "]+$", "g"), '');
            };

            ctrl.formattedText = function () {
                const tokensAndReplacements = ctrl.tweet.text
                    .split(' ')
                    .filter(w => w.length > 0)
                    .map(word => trim(word, '(:;.,)')) // symmetric trim on server-side, poor man's tokenizer :)
                    .map(word => {
                        if (word.indexOf('#') === 0) {
                            const hashtag = word.substring(1, word.length);
                            return [word, '<a href="https://twitter.com/hashtag/' + hashtag + '?src=hash">#' + hashtag + '</a>'];
                        }

                        if (word.indexOf('@') === 0) {
                            const mention = word.substring(1, word.length);
                            return [word, '<a href="https://twitter.com/' + mention + '">@' + mention + '</a>'];
                        }

                        if (word.indexOf(tCo) === 0) {
                            const shortened = word.substring(tCo.length, word.length);
                            return [word, '<a href="https://t.co/' + shortened + '">pic.twitter.com/' + shortened + '</a>'];
                        }

                        return [];
                    })
                    .filter(pairArr => pairArr.length > 0);

                const words = tokensAndReplacements.reduce((acc, curr) => {
                    return acc.replace(curr[0], curr[1]);
                }, ctrl.tweet.text);

                return '<p>' + words + '</p>';
            };
        },
        templateUrl: 'templates/tweet.html',
        bindings: {
            tweet: '<'
        }
    });
})(angular, Rx);