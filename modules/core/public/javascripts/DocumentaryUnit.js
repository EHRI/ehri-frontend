!(function () {
  angular.module('DocumentaryUnit', ["ngSanitize", 'ui.bootstrap' ], function ($provide) {
    $provide.factory('$portal', function ($http, $log) {
      return {
        search: function (type, searchTerm, page) {
          var params = "?q=" + (searchTerm || "");
          if (page) {
            params = params + "&limit=10&page=" + page;
          }
          // $log.log("Searching with: ", "/search/" + type + params)
          return $http.get("/filter/" + type + params);
        },

        detail: function (type, id) {
          return $http.get("/api/" + type + "/" + id);
        },

        saveAnnotations: function (id, args) {
          var postArgs = {
            method: "POST",
            url: "/docs/linkm/" + id,
            data: args.join("&"),
            headers: {'Content-Type': 'application/x-www-form-urlencoded', 'ajax-ignore-csrf': true}
          };
          $log.log("Post args...", postArgs);
          return $http(postArgs);
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
  })/*.config(['$routeProvider', function($routeProvider, $locationProvider) {
   $routeProvider.
   when('/', {templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',   controller: DocumentaryCtrl}).
   otherwise({redirectTo: '/'});
   }])*/;
}).call(this);

function LinkCtrl($scope, $window, $portal, $service, dialog, $rootScope) {
// Items data
  $scope.selected = []; //Basket items
  $scope.id = $window.ITEM_ID; //Source documentaryUnit
  $scope.tempSelected = null; //Item in editing

  $scope.item = null; // Preview item
  $scope.type = 'documentaryUnit'; // Type of query
  $scope.results = []; // Results from query

  $scope.currentPage = 1; //Current page of pagination
  $scope.maxSize = 5; // Number of pagination buttons shown
  $scope.numPages = false; // Number of pages (get from query)

  //$scope.AccessTypeList = [{id: "creatorAccess", name:"Creators"}, {id: "personAccess", name: "Persons"}, {id: "corporateBodyAccess", name: "Corporate Bodies"}, {id: "subjectAccess", name:"Subject Access Points"}, {id:"placeAccess", name:"Places"}, {id: "otherAccess", name: "Others"}];
  //$scope.LinkTypeList = [{id: "associative", name:"associative"}, {id: "hierarchical", name: "hierarchical"}, {id: "temporal", name: "temporal"}, {id: "family", name:"family"}];

  $scope.initiate = function () {
    if ($rootScope.typeAccess) {
      $scope.accessType = $rootScope.typeAccess;
    }
    else {
      $scope.accessType = "creatorAccess";
    }
    $scope.linkAccessType = "associative";
  }
  $scope.initiate();
//----------------------------------------------------------------------------\\
//Functions
  /*
   *
   *	Pagination
   *
   */
  //Trigger moreResults if current page changes
  $scope.$watch("currentPage", function (newValue, oldValue) {
    if (!$scope.results[newValue]) {
      $scope.moreResults($scope.q);
    }
  });

  //Trigger a new research, ie. reset results
  $scope.doSearch = function (searchTerm, page) {
    return $portal.search($scope.type, searchTerm, page).then(function (response) {
      $scope.results = [];
      $scope.currentPage = response.data.page;
      $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
    });
  }

  //Trigger same research on a defined page
  $scope.moreResults = function (searchTerm) {
    return $portal.search($scope.type, searchTerm, $scope.currentPage).then(function (response) {
      // Append results instead of replacing them...
      $scope.currentPage = response.data.page;
      $scope.results[$scope.currentPage] = response.data.items;
      $scope.numPages = response.data.numPages;
      $scope.currentPage = response.data.page;
    });
  }

  $scope.setType = function (type) {
    $scope.type = type;
    $scope.doSearch("");
  }


  /*
   *
   *	Preview Part
   *
   */
  //Set item from results list to preview
  $scope.setItem = function (item) {
    if ($scope.item === item) {
      $scope.item = $scope.itemData = null;
    } else {
      $scope.item = item;
      return $portal.detail($scope.type, item).then(function (response) {
        $scope.itemData = response.data;
        if ($rootScope.mode == "AccessPage") {
          $scope.addTemp();
        }
      });
    }
  }
  //Send preview item to Edit item
  $scope.addTemp = function () {
    $scope.tempSelected = {
      id: $scope.itemData.id,
      type: $scope.itemData.type,
      name: $scope.itemData.relationships.describes[0].data.name
    };
  }

  /*
   *
   *	From edition to basket
   *
   */
  //Edit item From Basket
  $scope.editItem = function (item) {
    $scope.tempSelected = item;
  }
  //Close editing board
  $scope.closeEdit = function () {
    $scope.tempSelected = null;
  }
  //Send edited item to basket
  $scope.addSelected = function (item) {
    //Adding Title and Desc
    $scope.tempSelected = item;
    $scope.tempSelected["linkTitle"] = $scope.linkTitle;
    $scope.tempSelected["linkDesc"] = $scope.linkDesc;
    $scope.tempSelected["linkType"] = $scope.linkType;

    $scope.tempSelected["linkAccessType"] = $scope.linkAccessType;
    $scope.tempSelected["accessType"] = $scope.accessType;

    //Reset scopes
    $scope.linkTitle = "";
    $scope.linkDesc = "";
    $scope.linkType = "";

    //Test FIX
    //$scope.linkAccessType = $rootScope.typeAccess;
    //$scope.accessType = $scope.accessType;

    if ($rootScope.LinkMode == "Link") {
      //Cleaning Results
      $scope.removeSelected($scope.tempSelected.id);

      //Pushing to results
      $scope.selected.push($scope.tempSelected);
    } else {
      $scope.selected = $scope.tempSelected;
    }

    //Cleaning Temp
    $scope.tempSelected = null;
  }

  /*
   *
   *	From basket to server
   *
   */
  $scope.save = function () {
    if ($rootScope.LinkMode == "Link") {
      var args = [];
      $scope.selected.forEach(function (ele, idx) {
        console.log(ele);
        var s = "link[" + idx + "].id=" + ele.id + "&" +
            "link[" + idx + "].data.type=associative&" +
            "link[" + idx + "].data.description=Test Annotation";
        args.push(s)
      });
      return $portal.saveAnnotations($scope.id, args).then(function (response) {
        if ($rootScope.mode == "AccessPage") {
          $rootScope.getAccess();
          $scope.close();
        }
        else {
          $window.location = $service.redirectUrl($scope.id);
        }
      });
    }
    else if ($rootScope.LinkMode == "Access") {

      if ($rootScope.mode == "AccessPage") {
        $scope.addSelected($scope.tempSelected);
      }
      $service.createLink($scope.id, $rootScope.AccessItem.id).ajax({
        data: JSON.stringify({target: $scope.selected.id, description: $scope.selected.linkDesc}),
        headers: {"ajax-ignore-csrf": true, "Content-Type": "application/json"},
        dataType: "json",
        success: function (data) {
          console.log(data);
          if ($rootScope.mode == "AccessPage") {
            $rootScope.getAccess();
            $scope.close();
          }
          else {
            $window.location = $service.redirectUrl($scope.id);
          }
        }
      });
    }
    else if ($rootScope.LinkMode == "AddAccess") {
      if ($rootScope.mode == "AccessPage") {
        $scope.addSelected($scope.tempSelected);
      }
      var headers = {"ajax-ignore-csrf": true, "Content-Type": "application/json"};

      // Here we need to first create an access point, and then create the link
      // to the target object.
      $service.createAccessPoint($scope.id, $rootScope.descriptionID).ajax({
        data: JSON.stringify({
          name: $scope.selected.name,
          type: $scope.selected.accessType,
          description: $scope.selected.linkDesc
        }),
        headers: headers
      }).done(function (data) {
         $service.createLink($scope.id, data.id).ajax({
           data: JSON.stringify({
             target: $scope.selected.id,
             type: $scope.selected.linkAccessType,
             description: $scope.selected.linkDesc
           }),
           headers: headers
         }).done(function(data) {
           if ($rootScope.mode == "AccessPage") {
             $rootScope.getAccess();
             $scope.close();
           }
           else {
             $window.location = $service.redirectUrl($scope.id);
           }
         });
      });
    }
  }

  $scope.removeSelected = function (id) {
    $scope.selected = $scope.selected.filter(function (ele, idx, arr) {
      return (ele.id !== id);
    });
  }

  $scope.close = function (result) {
    dialog.close(result);
  };

//**********************************************\\
  //Triger Search
  $scope.doSearch("");

}

function DocumentaryCtrl($scope, $service, $dialog, $rootScope, $window) {
  $scope.modalLink = {	//Options for modals
    backdrop: true,
    keyboard: true,
    backdropClick: true,
    dialogClass: "modal modal-test",
    templateUrl: ANGULAR_ROOT + '/partials/search-list.tpl.html',
    controller: 'LinkCtrl'
  };
  $scope.accesslist = [];
  $accessTitle = "Access Points";

  /*
   *
   *	Dialog Opening
   *
   */
  $scope.openModalLink = function () {
    $rootScope.LinkMode = "Link";

    var d = $dialog.dialog($scope.modalLink);
    d.open().then(function (result) {
      return true;
    });
  }
  $scope.openModalAccess = function (idAccess, textAccess) {
    $rootScope.AccessItem = { id: idAccess, text: textAccess };
    $rootScope.LinkMode = "Access";

    var d = $dialog.dialog($scope.modalLink);
    d.open().then(function (result) {
      return true;
    });
  }
  $scope.addAccessModal = function (descID) {
    $rootScope.LinkMode = "AddAccess";
    $rootScope.descriptionID = descID;

    var d = $dialog.dialog($scope.modalLink);
    d.open().then(function (result) {
      return true;
    });
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
              success: function () {
                var ok = true;
                if ($rootScope.mode == "AccessPage") {
                  $rootScope.getAccess();
                }
              }
            });
          }
        });
  };

  $scope.deleteAccessPoint = function (accessPointID, accessLinkText) {
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
            $service.deleteAccessPoint($window.ITEM_ID, accessPointID).ajax({
              success: function () {
                var ok = true;
                if ($rootScope.mode == "AccessPage") {
                  $rootScope.getAccess();
                }
              }
            });
          }
        });
  };

  $rootScope.getAccess = function () {
    $service.getAccessPoints($window.ITEM_ID, $window.DESC_ID).ajax({
      success: function (data) {
        $scope.accesslist = data[0];
        console.log($scope.accesslist.id);
        $scope.$apply()
      }
    });
  }
  if ($window.DESC_ID) {
    $rootScope.mode = "AccessPage";
    $rootScope.getAccess();
  }
}