'use strict';

(function (angular, Rx) {

    var keywordsMessage = function keywordsMessage(keywordsString) {
        return JSON.stringify({keywords: keywordsString.split(' ')});
    };

    var tweetmap = angular.module('tweetmap', ['rx', 'ngSanitize', 'ngMap']);

    var tCo = 'https://t.co/';

    var maxTweets = 50;

    var addTweet = function addTweet(tweetCollection) {
        return function (tweet) {
            if (tweetCollection.length >= maxTweets) {
                tweetCollection.pop();
                tweetCollection.unshift(tweet);
            } else {
                tweetCollection.unshift(tweet);
            }
        };
    };

    tweetmap.service('DataStream', function () {
        var socket = Rx.DOM.fromWebSocket((document.location.protocol === 'https:' ? 'wss://' : 'ws://') + window.location.host + '/ws', null);

        return {
            stream: socket.map(function (msg) {
                return JSON.parse(msg.data);
            }).share(),
            socket: socket
        };
    });

    tweetmap.component('tweetMap', {
        controller: function controller($scope, $element, DataStream) {
            var ctrl = this;

            ctrl.tweets = [];

            var addTweetWithGeo = addTweet(ctrl.tweets);

            DataStream.stream.filter(function (tweet) {
                return null !== tweet.maybeGeoLocation;
            }).subscribe(function (tweetWithGeo) {
                addTweetWithGeo(tweetWithGeo);
                $scope.$apply();
            }, function (e) {
                return console.error('error: %s', e);
            }, function () {
                return console.info('socket closed');
            });
        },
        templateUrl: 'templates/map.html'
    });

    tweetmap.component('filterInput', {
        controller: function controller($scope, $element, DataStream) {
            Rx.Observable.fromEvent($element.find('input'), 'keyup').map(function (e) {
                return e.target.value;
            }).debounce(700).map(function (keyword) {
                return keyword.trim();
            }).filter(function (keyword) {
                return keyword.length > 0;
            }).distinctUntilChanged().forEach(function (keyword) {
                return DataStream.socket.onNext(keywordsMessage(keyword));
            });

            Rx.Observable.fromEvent($element.find('button'), 'click').forEach(function () {
                $element.find('input').val('');
                DataStream.socket.onNext(keywordsMessage(''));
            });
        },
        templateUrl: 'templates/filterInput.html'
    });

    tweetmap.component('tweets', {
        controller: function controller($scope, DataStream) {
            var ctrl = this;

            ctrl.tweets = [];

            var addTweetToList = addTweet(ctrl.tweets);

            DataStream.stream.subscribe(function (tweet) {
                addTweetToList(tweet);
                $scope.$apply();
            }, function (e) {
                return console.error('error: %s', e);
            }, function () {
                return console.info('socket closed');
            });
        },
        templateUrl: 'templates/tweets.html'
    });

    tweetmap.component('tweet', {
        controller: function controller() {
            var ctrl = this;

            var escapeRegex = function escapeRegex(string) {
                return string.replace(/[\[\](){}?*+\^$\\.|\-]/g, "\\$&");
            };

            var trim = function trim(string, chars) {
                var characters = escapeRegex(chars);
                return string.replace(new RegExp("^[" + characters + "]+|[" + characters + "]+$", "g"), '');
            };

            ctrl.formattedText = function () {
                var tokensAndReplacements = ctrl.tweet.text.split(' ').filter(function (w) {
                    return w.length > 0;
                }).map(function (word) {
                    return trim(word, '(:;.,)');
                }) // symmetric trim on server-side, poor man's tokenizer :)
                    .map(function (word) {
                        if (word.indexOf('#') === 0) {
                            var hashtag = word.substring(1, word.length);
                            return [word, '<a href="https://twitter.com/hashtag/' + hashtag + '?src=hash">#' + hashtag + '</a>'];
                        }

                        if (word.indexOf('@') === 0) {
                            var mention = word.substring(1, word.length);
                            return [word, '<a href="https://twitter.com/' + mention + '">@' + mention + '</a>'];
                        }

                        if (word.indexOf(tCo) === 0) {
                            var shortened = word.substring(tCo.length, word.length);
                            return [word, '<a href="https://t.co/' + shortened + '">pic.twitter.com/' + shortened + '</a>'];
                        }

                        return [];
                    }).filter(function (pairArr) {
                        return pairArr.length > 0;
                    });

                var words = tokensAndReplacements.reduce(function (acc, curr) {
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