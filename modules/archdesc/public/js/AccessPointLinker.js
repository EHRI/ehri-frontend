!(function() {
  /*
  *
  *
  *   Migration from 0.5.0 needs to recreate a function for dialomessageBox
  *
  *   From : https://github.com/angular-ui/bootstrap/issues/996
  *
  *
  */
    var dialogModule = angular.module("dialog", [ 'ui.bootstrap']);


    dialogModule.factory('$dialog', ['$rootScope', '$modal', function($rootScope, $modal) {

      function dialog(modalOptions, resultFn) {
        var dialog = $modal.open(modalOptions);
        if (resultFn) dialog.result.then(resultFn);
        dialog.values = modalOptions;
        return dialog;
      }

      function modalOptions(templateUrl, controller, scope) {
        return { templateUrl:  templateUrl, controller: controller, scope: scope }; }

      return {
        /**
         * Creates and opens dialog.
         */
        dialog: dialog,

        /**
         * Returns 0-parameter function that opens dialog on evaluation.
         */
        simpleDialog: function(templateUrl, controller, resultFn) {
          return function () { return dialog(modalOptions(templateUrl, controller), resultFn); }; },

        /**
         * Opens simple generic dialog presenting title, message (any html) and provided buttons.
         */
        messageBox: function(title, message, buttons, resultFn) {
          var scope = angular.extend($rootScope.$new(false), { title: title, message: message, buttons: buttons });
          return dialog(modalOptions("template/messageBox/message.html", 'MessageBoxController', scope), function (result) {
            var value = resultFn ? resultFn(result) : undefined;
            scope.$destroy();
            return value;
          }); }
      };
    }]);


    dialogModule.run(["$templateCache", function($templateCache) {
      $templateCache.put("template/messageBox/message.html",
          '<div class="modal-header"><h3>{{ title }}</h3></div>\n' +
          '<div class="modal-body"><p ng-bind-html="message"></p></div>\n' +
          '<div class="modal-footer"><button ng-repeat="btn in buttons" ng-click="close(btn.result)" class="btn" ng-class="btn.cssClass">{{ btn.label }}</button></div>\n');
    }]);


    dialogModule.controller('MessageBoxController', ['$scope', '$modalInstance', function ($scope, $modalInstance) {
      $scope.close = function (result) { $modalInstance.close(result); }
    }]);

  /*
  *
  *
  *     PROPER APPLICATION
  *
  *
  */


  var linker = angular.module('AccessPointLinker', ["ngSanitize", "dialog", 'ui.bootstrap.modal', 'ui.bootstrap.typeahead', 'ui.bootstrap.pagination' ], function($provide) {
    $provide.factory('$search', function($http, $log, $service) {
      var search = function(types, searchTerm, page) {
        var params = "?limit=10&q=" + (searchTerm || "");
        if (types && types.length > 0) {
          if(Array.isArray(types)) {
            params = params + "&" + (types.map(function(t) { return "st[]=" + t }).join("&"));
          } else {
            params = params + "&st[]=" + types;
          }
        }
        if (page > 0) {
          params = params + "&page=" + page;
        }
        return $http.get($service.filter().url + params);
      };
      var limitTypes = function(type) {
        if(Array.isArray(type)) {
          return type;
        }
        if (type.match(/(?:creator|person)Access/)) {
          return ["historicalAgent"];
        }
        if (type.match(/(?:place)Access/)) {
          return ["cvocConcept", "country"];
        }
        if (type === "subjectAccess") {
          return ["cvocConcept"];
        }
        return [];
      };
      return {
        search: function(type, searchTerm, page) {
          return search(this.limitTypes(type), searchTerm, page);
        },
        limitTypes : limitTypes,
        filter: function(type, searchTerm, page) {
          return search(this.limitTypes(type), (searchTerm || "PLACEHOLDER_NO_RESULTS"), page);
        },

        detail: function(type, id) {
          return $http.get($service.getItem(type, id).url, {
            headers: {
              "Content-Type": "application/json",
              "Accept": "application/json; charset=utf-8"
            }
          });
        }
      };
    });

    $provide.factory('$service', function() {
      return {
        get: jsRoutes.controllers.core.Application.get,
        getItem: jsRoutes.controllers.core.ApiController.getItem,
        filter: jsRoutes.controllers.core.SearchFilter.filter,
        createLink: jsRoutes.controllers.archdesc.DocumentaryUnits.createLink,
        createMultipleLinks: jsRoutes.controllers.archdesc.DocumentaryUnits.linkMultiAnnotatePost,
        createAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.createAccessPoint,
        getAccessPoints: jsRoutes.controllers.archdesc.DocumentaryUnits.getAccessPointsJson,
        deleteLink: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteLink,
        deleteAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteAccessPoint,
        deleteLinkAndAccessPoint: jsRoutes.controllers.archdesc.DocumentaryUnits.deleteLinkAndAccessPoint,
        redirectUrl: function(id) {
          return jsRoutes.controllers.archdesc.DocumentaryUnits.get(id).url;
        }
      };
    });

    $provide.factory('$names', function() {
      return {
        cvocConcept: "Concept/Keyword",
        cvocVocabulary: "Vocabulary",
        documentaryUnit: "Archival Unit",
        repository: "Repository",
        historicalAgent: "Authority"
      }
    });

  });

  //
  // Directives for testing, these aren't used yet...
  //
  linker.directive('ngEnter', function() {
    return function(scope, elem, attrs) {
      elem.bind('keypress', function(e) {
        if (e.charCode === 13) scope.$apply(attrs.ngEnter);
      });
    };
  });

  linker.directive('ngKeyNav', function() {
    return function(scope, elem, attrs) {
      elem.bind('keypress', function(e) {
        if (e.charCode === 38) scope.$apply(attrs.ngKeyNav);
        else if (e.charCode === 40) scope.$apply(attrs.ngKeyNav);
      });
    };
  });
  linker.directive('selectOnClick', function() {
    // Linker function
    return function(scope, element, attrs) {
      element.click(function() {
        element.select();
      });
    };
  });

}).call(this);


function LinkCtrl($scope, $window, $search, $names, $rootScope, $modalInstance, q) {
  // Items data
  // FIXME: Retrieving the passed-in query should be easier!
  $scope.q = q;
  $scope.item = null; // Preview item
  $scope.type = $rootScope.tempAccessPoint.type; // Type of query
  $scope.availableTypes = [];
  var temptypes = $search.limitTypes($rootScope.tempAccessPoint.type);
  if(temptypes.length > 0) {
    $scope.availableTypes = temptypes;
  }

  $scope.results = []; // Results from query

  $scope.pageInfos = {
    currentPage : 1, //Current page of pagination
    maxSize : 5, // Number of pagination buttons shown
    numPages : false  // Number of pages (get from query)
  };

  // Trigger moreResults if current page changes
  $scope.$watch("pageInfos.currentPage", function(newValue, oldValue) {
    if (!$scope.results[newValue]) {
      $scope.moreResults();
    }
  });

  $rootScope.readableType = function(code) {
    return $names[code] ? $names[code] : code;
  };

  /**
   * Trigger a search for the current search term.
   * @param searchTerm
   * @param page
   * @returns {*}
   */
  $scope.doSearch = function(searchTerm, page) {
    return $search.search($scope.type, searchTerm, page).then(function(response) {
      $scope.results = [];
      $scope.pageInfos.currentPage = parseInt(response.data.page);
      $scope.results[$scope.pageInfos.currentPage] = response.data.items;
      $scope.pageInfos.numPages = parseInt( response.data.numPages);

    });
  };

  /**
   * Fetch more results for the current search term.
   * @param searchTerm
   * @returns {*}
   */
  $scope.moreResults = function(searchTerm) {
    return $search.search($scope.type, $scope.q, $scope.pageInfos.currentPage).then(function(response) {
      // Append results instead of replacing them...
      $scope.pageInfos.currentPage = parseInt(response.data.page);
      $scope.results[$scope.pageInfos.currentPage] = response.data.items;
      $scope.pageInfos.numPages = parseInt(response.data.numPages);
      $scope.pageInfos.currentPage = parseInt(response.data.page);

    });
  };

  /**
   * Narrow the search to a particular type of item.
   * @param type
   */
  $scope.setType = function(type) {
    if(typeof type === "undefined") {
      type = $rootScope.tempAccessPoint.type;
    } else {
      type = [type];
    }
    $scope.type = $search.limitTypes(type);
    $scope.doSearch($scope.q);
  };

  /**
   * Select a particular item. A second click will un-select
   * it again.
   * @param item
   * @returns {*}
   */
  $scope.setItem = function(item) {
    if ($scope.item === item) {
      $scope.item = $scope.itemData = null;
    } else {
      $scope.item = item;
      return $search.detail(item[2], item[0]).then(function(response) {
        $scope.itemData = response.data;
      });
    }
  };

  /**
   * Select an item, closing the dialog.
   */
  $scope.selectItem = function() {
    if ($scope.item)
      $scope.close($scope.item);
  };

  /**
   * Close the dialog.
   * @param result
   */
  $scope.close = function(result) {
    $modalInstance.close(result);
  };
}


function LinkerCtrl($scope, $service, $search, $dialog, $names, $rootScope, $window, $modal) {
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
  $scope.modalLink = {  //Options for modals
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

  $scope.readableType = function(code) {
    return $names[code] ? $names[code] : code;
  };

  /**
   * Initialize the scope with the item and description IDs.
   * @param  {[type]} itemId        item ID
   * @param  {[type]} descriptionId description ID
   */
  $scope.init = function(itemId, descriptionId) {
    $scope.itemId = itemId;
    $scope.descriptionId = descriptionId;
    $scope.getAccessPointList();
  };

  /* Nasty stateful var tracking an access point
   * in the process of being created.
   * {
   *     type: "otherAccess",
   *     name: "Some text",
   *     description: "",
   *     link: {
   *       type: "associative",
   *       target: "some-other-item-id"
   *     }
   * }
   *
   */
  $scope.tempAccessPoint = null;

  // List of access points populated via Ajax
  $scope.accesslist = [];

  // Matches for other items to (potentially) link to
  $scope.matches = [];

  /**
   * Determine if we're currently in the middle of adding an access point.
   * @param type
   * @returns {boolean}
   */
  $scope.editInProgress = function(type) {
    return $scope.tempAccessPoint !== null && $scope.tempAccessPoint.type == type;
  };

  /**
   * Determine if the access point currently being added is valid.
   * @param type
   * @returns {boolean}
   */
  $scope.hasValidNewAccessPoint = function(type) {
    return $scope.tempAccessPoint !== null
      && $scope.tempAccessPoint.type == type
      && $scope.tempAccessPoint.name != "";
  };

  /**
   * Delete an access point with a link.
   * TODO: Reduce duplication here.
   * @param accessPointId
   * @param accessLinkId
   * @param accessLinkText
   */
  $scope.deleteAccessPointWithLink = function(accessPointId, accessLinkId, accessLinkText) {
    var title = 'Delete link for access point';
    var msg = 'Are you sure you want to delete the link for ' + accessLinkText + ' ?';
    var btns = [
      {result: 0, label: 'Cancel'},
      {result: 1, label: 'OK', cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns, function(result) {
        if (result == 1) {
          $service.deleteLinkAndAccessPoint($scope.itemId, $scope.descriptionId, accessPointId, accessLinkId).ajax({
            success: function(data) {
              $scope.getAccessPointList();
            }
          });
        }
      });
  };

  /**
   * Open a dialog to browse for specific items.
   */
  $scope.openBrowseDialog = function() {
    $rootScope.tempAccessPoint = $scope.tempAccessPoint;
    var d = $modal.open($scope.modalLink);
    d.result.then(function(result) {
      if (result) {
        $scope.selectLinkMatch(result);
      }
      return true;
    });
  };

  /**
   * When the user clicks a potenial match, add it
   * as the link target.
   * @param match
   */
  $scope.selectLinkMatch = function(match) {
    // FIXME: Overwriting the user's typed-in text is
    // perhaps not the best behaviour to use here!
    $scope.tempAccessPoint.name = match[1];
    $scope.tempAccessPoint.link = {
      target: match[0],
      name: match[1],
      targetType: match[2],
      type: "associative"
    };
    // Clear the list of matches
    $scope.matches = [];
  };

  /**
   * Delete an access point.
   * @param accessPointId
   * @param accessLinkText
   */
  $scope.deleteAccessPoint = function(accessPointId, accessLinkText) {
    var title = 'Delete access point';
    var msg = 'Are you sure you want to delete this access point: ' + accessLinkText + ' ?';
    var btns = [
      {result: 0, label: 'Cancel'},
      {result: 1, label: 'OK', cssClass: 'btn-primary'}
    ];

    $dialog.messageBox(title, msg, btns, function(result) {
        if (result == 1) {
          console.log("Deleting access point", accessPointId);

          $service.deleteAccessPoint($scope.itemId, $scope.descriptionId, accessPointId).ajax({
            headers: {"Accept": "application/json; charset=utf-8"},
            success: function(data) {
              console.log("Access point delete result: ", data)
              if (data === true) {
                $scope.getAccessPointList();
              }
            }
          });
        }
      });
  };

  /**
   * Select from the full list of access points, only those with the given type.
   * @param type
   * @returns accessPoints a list of access points with the given type.
   */
  $scope.getAccessPointsWithType = function(type) {
    for (idx in $scope.accesslist.data) {
      if ($scope.accesslist.data[idx].type === type) {
        return $scope.accesslist.data[idx].data;
      }
    }
  };

  /**
   *
   * @param type
   */
  $scope.initialiseEdit = function(type) {
    $scope.tempAccessPoint = {
      type: type,
      name: "",
      description: "",
      link: null
    }
  }

  /**
   * Populate the matches member with those that
   * match the (partial) name string entered by
   * the user.
   */
  $scope.queryNameMatches = function() {
    if (!$scope.hasValidNewAccessPoint) {
      $scope.matches = [];

    }

    $search.filter($scope.tempAccessPoint.type, $scope.tempAccessPoint.name).then(function(result) {
      $scope.matches = result.data.items;
    });
  }

  /**
   * Save the new access point data to the server, refreshing
   * the list of access points when done.
   */
  $scope.saveNewAccessPoint = function() {
    $service.createAccessPoint($scope.itemId, $scope.descriptionId).ajax({
      data: angular.toJson({
        name: $scope.tempAccessPoint.name,
        accessPointType: $scope.tempAccessPoint.type,
        isA: "relationship",
        description: $scope.tempAccessPoint.description
      }),
      headers: ajaxHeaders
    }).done(function(data) {
        if ($scope.tempAccessPoint.link === null) {
          $scope.cancelAddAccessPoint();
          $scope.getAccessPointList();
        } else {
          $service.createLink($scope.itemId, data.id).ajax({
            data: angular.toJson({
              target: $scope.tempAccessPoint.link.target,
              type: $scope.tempAccessPoint.link.type,
              description: $scope.tempAccessPoint.description
            }),
            headers: ajaxHeaders
          }).done(function(data) {
              $scope.cancelAddAccessPoint();
              $scope.getAccessPointList();
            }).error(function() {
              console.log(arguments)
            });
        }
      });
  };

  /**
   * Get a URL for an item given only its ID.
   * @param id
   * @returns url the (local) URL
   */
  $scope.getUrl = function(id) {
    return $service.get(id).url;
  };

  /**
   * Get a URL for an item given its type and id. This is more
   * efficient, since it does not require a redirect.
   * @param type
   * @param id
   * @returns url the (local) URL
   */
  $scope.getTypeUrl = function(type, id) {
    return $service.getItem(type, id).url;
  };

  /**
   * Cancel the operation of adding an access point.
   */
  $scope.cancelAddAccessPoint = function() {
    $scope.tempAccessPoint = null;
    $scope.matches = [];
  };

  /**
   * Remove just the linked item when adding an access point
   * (if, for example, the user selected the wrong thing.)
   */
  $scope.removeTempAccessPointLink = function() {
    $scope.tempAccessPoint.link = null;
  };

  /**
   * Fetch the current list of access points for the current item and description.
   * The list is configured like so:
   */
  $scope.getAccessPointList = function() {
    $service.getAccessPoints($scope.itemId, $scope.descriptionId).ajax({
      success: function(data) {
        for (var i in data) {
          if (data[i].id === $scope.descriptionId) {
            $scope.accesslist = data[i];
            $scope.$apply();
            break;
          }
        }
      }
    });
  };
}
