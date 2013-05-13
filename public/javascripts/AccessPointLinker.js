!(function () {
  angular.module('AccessPointLinker', ["ngSanitize", 'ui.bootstrap.modal', 'ui.bootstrap.typeahead' ], function ($provide) {
    $provide.factory('$search', function ($http, $log) {
      var search = function (type, searchTerm, page) {
        var params = "?limit=10&q=" + (searchTerm || "");
        if (type) {
          params = params + "&type=" + type;
        }
        if (page) {
          params = params + "&page=" + page;
        }
        $log.log("Searching with: ", "/search/" + params)
        return $http.get("/filter" + params);
      }

      return {
        search: search,
        filter: function (type, searchTerm, page) {
          return search(type, (searchTerm || "PLACEHOLDER_NO_RESULTS"), page);
        },

        detail: function (type, id) {
          return $http.get("/api/" + type + "/" + id);
        }
      };
    });

    $provide.factory('$service', function() {
      return {
        get: jsRoutes.controllers.Application.get,
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


function LinkCtrl($scope, $window, $search, dialog) {
// Items data
  // FIXME: Retrieving the passed-in query should be easier!
  $scope.q = dialog.options.resolve.q();
  $scope.item = null; // Preview item
  $scope.type = ''; // Type of query
  $scope.results = []; // Results from query

  $scope.currentPage = 1; //Current page of pagination
  $scope.maxSize = 5; // Number of pagination buttons shown
  $scope.numPages = false; // Number of pages (get from query)

  //Trigger moreResults if current page changes
  $scope.$watch("currentPage", function (newValue, oldValue) {
    if (!$scope.results[newValue]) {
      $scope.moreResults($scope.q);
    }
  });

  //Trigger a new research, ie. reset results
  $scope.doSearch = function (searchTerm, page) {
    return $search.search($scope.type, searchTerm, page).then(function (response) {
      $scope.results = [];
      $scope.currentPage = response.data.page;
      $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
    });
  }

  //Trigger same research on a defined page
  $scope.moreResults = function (searchTerm) {
    return $search.search($scope.type, searchTerm, $scope.currentPage).then(function (response) {
      // Append results instead of replacing them...
      $scope.currentPage = response.data.page;
      $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
    });
  }

  $scope.setType = function (type) {
    $scope.type = type;
    $scope.doSearch($scope.q);
  }

  $scope.setItem = function (item) {
    if ($scope.item === item) {
      $scope.item = $scope.itemData = null;
    } else {
      $scope.item = item;
      return $search.detail(item[2], item[0]).then(function (response) {
        $scope.itemData = response.data;
      });
    }
  }

  //Send edited item to basket
  $scope.selectItem = function () {
    if ($scope.item)
      $scope.close($scope.item);
  }

  $scope.close = function (result) {
    console.log("Closing with: " + $scope.item)
    dialog.close(result);
  };
}



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
   * Modal dialog configuration
   */
  $scope.modalLink = {	//Options for modals
    backdrop: true,
    keyboard: true,
    backdropClick: true,
    dialogClass: "modal modal-test",
    templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',
    resolve: {
      q: function() {
        return angular.copy($scope.tempAccessPoint.name);
      }
    },
    controller: 'LinkCtrl'
  };

  /**
   * Initialize the scope with the item and description IDs.
   * @param  {[type]} itemId        item ID
   * @param  {[type]} descriptionId description ID
   */
  $scope.init = function(itemId, descriptionId) {
    $scope.itemId = itemId;
    $scope.descriptionId = descriptionId;
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

  $scope.test = ["foo", "bar", "baz"]

  $scope.editInProgress = function(type) {
    return $scope.tempAccessPoint !== null && $scope.tempAccessPoint.type == type;
  }

  $scope.hasValidNewAccessPoint = function(type) {
    return $scope.tempAccessPoint !== null
      && $scope.tempAccessPoint.type == type
      && $scope.tempAccessPoint.name != "";
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

  $scope.openBrowseDialog = function() {
    var d = $dialog.dialog($scope.modalLink);
    d.open().then(function (result) {
      if (result) {
        $scope.selectLinkMatch(result);
      }
      return true;
    });
  }

  $scope.selectLinkMatch = function(match) {
    console.log("Got link match: " + match)
    // FIXME: Overwriting the user's typed-in text is
    // perhaps not the best behaviour to use here!
    $scope.tempAccessPoint.name = match[1];
    $scope.tempAccessPoint.link = {
      target: match[0],
      name: match[1],
      targetType: match[2],
      type: "associative"
    }
    // Clear the list of matches
    $scope.matches = [];
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
    $scope.tempAccessPoint = {
      type: type,
      name: "",
      description: "",
      link: null
    }
  }

  $scope.queryNameMatches = function() {
    console.log("Query name matches: ")
    if (!$scope.hasValidNewAccessPoint) {
      $scope.matches = [];

    }

    $search.filter(null, $scope.tempAccessPoint.name).then(function(result) {
      $scope.matches = result.data.items;
    });
  }

  $scope.doSearch = function(text) {
    return $search.filter(null, text).then(function(result) {
      console.log(result.data);
      return result.data.items.map(function(i) {
        console.log(i[1])
        return i[1];
      });
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

  $scope.getUrl = function(id) {
    return $service.get(id).url;
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
