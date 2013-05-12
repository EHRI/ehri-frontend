!(function () {
  angular.module('AccessPointLinker', ["ngSanitize", 'ui.bootstrap' ], function ($provide) {
    $provide.factory('$search', function ($http, $log) {
      return {
        search: function (type, searchTerm, page) {
          var params = "?q=" + (searchTerm || "PLACEHOLDER_SEARCH_QUERY");
          if (type) {
            params = params + "&type=" + type;
          }
          if (page) {
            params = params + "&limit=10&page=" + page;
          }
          // $log.log("Searching with: ", "/search/" + type + params)
          return $http.get("/filter" + params);
        },

        detail: function (type, id) {
          return $http.get("/api/" + type + "/" + id);
        }
      };
    });

    $provide.factory('$service', function() {
      return {
        filter: jsRoutes.controllers.Search.filterType,
        createLink: jsRoutes.controllers.DocumentaryUnits.createLink,
        createMultipleLinks: jsRoutes.controllers.DocumentaryUnits.createMultipleLinks,
        createAccessPoint: jsRoutes.controllers.DocumentaryUnits.createAccessPoint,
        getAccessPoints: jsRoutes.controllers.DocumentaryUnits.getAccessPointsJson,
        deleteLink: jsRoutes.controllers.Links.deletePost,
        deleteAccessPoint: jsRoutes.controllers.DocumentaryUnits.deleteAccessPoint,
        redirectUrl: function(id) {
          return jsRoutes.controllers.DocumentaryUnits.get(id).url;
        }
      };
    });
  });
}).call(this);


function LinkerCtrl($scope, $service, $search, $dialog, $rootScope, $window) {

  /**
   * Headers for ajax calls.
   */
  var ajaxHeaders = {
    "ajax-ignore-csrf": true,
    "Content-Type": "application/json",
    "Accept": "application/json; charset=utf-8"
  };


  /**
   * Initialize the scope with the item and description IDs.
   * @param  {[type]} itemId        item ID
   * @param  {[type]} descriptionId description ID
   */
  $scope.init = function(itemId, descriptionId) {
    $scope.itemId = itemId;
    $scope.descriptionId = descriptionId;
    $rootScope.mode = "AccessPage";
    $scope.getAccess();
  }

  // Nasty stateful var tracking an access point
  // in the process of being created.
  // {
  //    type: "otherAccess",
  //    name: "Some text",
  //    description: "",
  //    link: {
  //      type: "associative",
  //      target: "some-other-item-id"
  //    }
  // }
  $scope.tempAccessPoint = null;

  // List of access points populated via Ajax
  $scope.accesslist = [];

  // Matches for other items to (potentially) link to
  $scope.matches = [];

  $scope.editInProgress = function(type) {
    return $scope.tempAccessPoint !== null && $scope.tempAccessPoint.type == type;
  }

  $scope.hasValidNewAccessPoint = function(type) {
    return $scope.tempAccessPoint !== null
      && $scope.tempAccessPoint.type == type
      && $scope.tempAccessPoint.name != "";
  }

  $scope.addSelectedAccess = function (type, descID) {
    $rootScope.typeAccess = type;
    $scope.addAccessModal(descID);
    console.log($rootScope.typeAccess);
  }

  $scope.deleteAccessLink = function (accessLinkID, accessLinkText) {
    var title = 'Delete link for access point';
    var msg = 'Are you sure you want to delete the link for ' + accessLinkText + ' ?';
    var btns = [
      {result: 0, label: 'Cancel'},
      {result: 1, label: 'OK', cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns)
        .open()
        .then(function (result) {
          if (result == 1) {
            $service.deleteLink(accessLinkID).ajax({
              headers: {"Accept": "application/json; charset=utf-8"},
              success: function (data) {
                if (data === true) {
                  $scope.getAccess();
                }
              }
            });
          }
        });
  };

  $scope.selectLinkMatch = function(match) {
    console.log("Got link match: " + match)
    $scope.tempAccessPoint.name = match[1];
    $scope.tempAccessPoint.link = {
      target: match[0],
      type: "associative"
    }
  }

  $scope.deleteAccessPoint = function (accessPointId, accessLinkText) {
    var title = 'Delete access point';
    var msg = 'Are you sure you want to delete this access point: ' + accessLinkText + ' ?';
    var btns = [
      {result: 0, label: 'Cancel'},
      {result: 1, label: 'OK', cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns)
        .open()
        .then(function (result) {
          if (result == 1) {
            $service.deleteAccessPoint($scope.itemId, accessPointId).ajax({
              headers: {"Accept": "application/json; charset=utf-8"},
              success: function (data) {
                if (data === true) {
                  $scope.getAccess();
                }
              }
            });
          }
        });
  };

  $scope.getAccessPointsWithType = function(type) {
    for (idx in $scope.accesslist.data) {
      if ($scope.accesslist.data[idx].type === type) {
        return $scope.accesslist.data[idx].data;
      }
    }
  }

  $scope.addNewAccessPoint = function(type) {
    console.log("Adding new with type: " + type);
    $scope.tempAccessPoint = {
      type: type,
      name: "",
      description: "",
      link: null
    }
  }

  $scope.queryNameMatches = function() {
    if (!$scope.hasValidNewAccessPoint) {
      $scope.matches = [];

    }

    $search.search(null, $scope.tempAccessPoint.name).then(function(result) {
      $scope.matches = result.data.items;
    });
  }

  $scope.saveNewAccessPoint = function() {
    $service.createAccessPoint($scope.itemId, $scope.descriptionId).ajax({
      data: JSON.stringify({
        name: $scope.tempAccessPoint.name,
        type: $scope.tempAccessPoint.type,
        description: $scope.tempAccessPoint.description
      }),
      headers: ajaxHeaders
    }).done(function(data) {
      if ($scope.tempAccessPoint.link === null) {
        $scope.cancelAddAccessPoint();
        $scope.getAccess();
      } else {
        $service.createLink($scope.itemId, data.id).ajax({
           data: JSON.stringify({
             target: $scope.tempAccessPoint.link.target,
             type: $scope.tempAccessPoint.link.type,
             description: $scope.tempAccessPoint.description
           }),
           headers: ajaxHeaders
        }).done(function(data) {
          $scope.cancelAddAccessPoint();
          $scope.getAccess();
        });
      }
    });
  }

  $scope.cancelAddAccessPoint = function() {
    $scope.tempAccessPoint = null;
    $scope.matches = [];
  }

  $scope.getAccess = function () {
    $service.getAccessPoints($scope.itemId, $scope.descriptionId).ajax({
      success: function (data) {
        $scope.accesslist = data[0];
        $scope.$apply()
      }
    });
  }
}
