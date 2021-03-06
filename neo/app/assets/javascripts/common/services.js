angular.module('cloudberry.common', [])
  .service('Asterix', function($http, $timeout, $location) {
    var startDate = new Date(2012, 1, 1, 0, 0, 0, 0);
    var ws = new WebSocket("ws://" + $location.host() + ":" + $location.port() + "/ws");
    var asterixService = {

      parameters: {
        dataset: "twitter",
        keywords: [],
        area: {
          swLog: -46.23046874999999,
          swLat: 53.85252660044951,
          neLog: -146.42578125,
          neLat: 21.453068633086783
        },
        time: {
          start: startDate,
          end: new Date()
        },
        level: "state",
        sampleOffset: 0,
        sampleLimit: 10,
        repeatDuration: 0
      },

      queryType: "search",

      mapResult: {},
      timeResult: {},
      hashTagResult: {},
      errorMessage: null,

      query: function(parameters, queryType) {
        var json = (JSON.stringify({
          dataset: parameters.dataset,
          keywords: parameters.keywords,
          area: parameters.area,
          timeRange : {
            start: queryType==='time' ? Date.parse(parameters.time.start) : Date.parse(startDate),
            end: queryType==='time' ? Date.parse(parameters.time.end) : Date.parse(new Date())
          },
          level: parameters.level,
          sampleOffset: parameters.sampleOffset,
          sampleLimit : parameters.sampleLimit,
          repeatDuration: parameters.repeatDuration
        }));
        ws.send(json);
      }
    };

    ws.onmessage = function(event) {
      $timeout(function() {
        console.log(event.data);
        var result = JSON.parse(event.data);
        switch (result.aggType) {
          case "map":
            asterixService.mapResult = result.result;
            break;
          case "time":
            asterixService.timeResult = result.result;
            break;
          case "hashtag":
            asterixService.hashTagResult = result.result;
            break;
          case "sample":
            asterixService.tweetResult = result.result;
            break;
          case "error":
            asterixService.errorMessage = result.errorMessage;
            break;
          default:
            console.log("ws get unknown data: " + result);
            break;
        }
      });
    };

    return asterixService;
  });
