!(function () {
	angular.module('ManageConcepts', ["ngSanitize", 'ui.bootstrap' ], function ($provide) {
		$provide.factory('$portal', function ($http, $log) {
			return {
				search: function (searchTerm, page, itemId) {
					var params = "?q=" + (searchTerm || "");
					if (page) {
						page = parseInt(page);
						page = ((page - 1) * 10 );
						if(page != 0) { page = page +1; }
						params = params + "&limit=10&offset=" + page;
					}
					else
					{
						params = params + "&limit=10";
					}

					if(!itemId)
					{
						return $http.get("/api/cvocConcept/page"+ params);
					}
					else
					{
						return $http.get("/api/cvocConcept/" + itemId + "/page" +   params);
					}
				}
				,getDetails: function (id) {
					return $http.get("/api/cvocConcept/" + id);
				}/*,

				saveAnnotations: function (id, args) {
					var postArgs = {
					method: "POST",
					url: "/docs/linkm/" + id,
					data: args.join("&"),
					headers: {'Content-Type': 'application/x-www-form-urlencoded', 'ajax-ignore-csrf': true}
					};
					$log.log("Post args...", postArgs);
					return $http(postArgs);
				}*/
			};
		});
/*
    $provide.factory('$service', function() {
      return {
        createLink: jsRoutes.controllers.DocumentaryUnits.createLink,
        addAccessPointWithLink: jsRoutes.controllers.DocumentaryUnits.createAccessPointLink,
        getAccessPoints: jsRoutes.controllers.DocumentaryUnits.getAccessPointsJson,
        deleteAccessPointLink: jsRoutes.controllers.Links.deletePost,
        deleteAccessPoint: jsRoutes.controllers.DocumentaryUnits.deleteAccessPoint,
        redirectUrl: function(id) {
          return jsRoutes.controllers.DocumentaryUnits.get(id).url;
        }
      };
    });
*/
	});
}).call(this);

function BrowseCtrl($scope, $portal, $window /*, $service*/) {
// Items data
$scope.parent = $window.TARGET_DATA;
console.log($scope.parent);
$scope.target = $scope.parent;
	
  $scope.basket = []; //Basket items

  $scope.item = null; // Preview item
  $scope.results = [{}, {}, {}]; // Results from query

  $scope.currentPage = [1,1,1];//Current page of pagination
  
  $scope.maxSize = 5; // Number of pagination buttons shown
  
  $scope.numPages = [false, false, false]; // Number of pages (get from query)
  
  $scope.children = [false, false, false]; // Array of itemId for children, 0 should not be changed
  $scope.childrenDetails = [false, false, false];
//----------------------------------------------------------------------------\\
//Functions
  /*
   *
   *	Pagination
   *
   */
   
	$scope.makePage = function(children, offset, limit) {
		var i = Math.ceil( offset / limit );
		if(i == 0) { i = 1; }
		$scope.currentPage[children] = i;
	}
	 //Trigger a new research, ie. reset results
	$scope.doSearch = function (searchTerm, page, children, itemId) {
	return $portal.search(searchTerm, page, (itemId || false)).then(function (response) {
		console.log(response);
		
	  $scope.results[children] = [];
	  $scope.makePage(children, response.data.offset, response.data.limit);
	  $scope.results[children][$scope.currentPage[children]] = response.data.values;
	  $scope.numPages[children] = Math.ceil( response.data.total / response.data.limit );
	  //console.log($scope.results[$scope.currentPage]);
	});
	}

	//Trigger same research on a defined page
	$scope.moreResults = function (searchTerm, children, itemId) {
	return $portal.search(searchTerm, $scope.currentPage[children], $scope.children[children]).then(function (response) {

	  // Append results instead of replacing them...
	  $scope.makePage(children, response.data.offset, response.data.limit);

	  $scope.results[children][$scope.currentPage[children]] = response.data.values;

	  $scope.numPages[children] = Math.ceil( response.data.total / response.data.limit );
	});
	}
//Triggers   
  //Trigger moreResults if current page changes
	$scope.$watch("currentPage[0]", function (newValue, oldValue) {
		if (!$scope.results[0][newValue] && $scope.currentPage[0] != 0) {
			console.log("Switch to page " + $scope.currentPage[0] + " for children 0");
			$scope.moreResults($scope.q, 0);
		}
	});
	$scope.$watch("currentPage[1]", function (newValue, oldValue) {
		if (!$scope.results[1][newValue] && $scope.currentPage[1] != 0 && ($scope.children[1])) {
			console.log("Switch to page " + $scope.currentPage[1] +" for children 1");
			$scope.moreResults("" , 1, $scope.children[1]);
		}
	});
	
	$scope.$watch("currentPage[2]", function (newValue, oldValue) {
		if (!$scope.results[2][newValue] && $scope.currentPage[2] != 0 && ($scope.children[2])) {
			console.log("Switch to page " + $scope.currentPage[2] +" for children 2");
			$scope.moreResults("" , 2, $scope.children[2]);
		}
	});
	
  //Trigger details
	$scope.$watch("children[1]", function (newValue) {
		if(newValue) {
			$portal.getDetails($scope.children[1]).then(function (response) {
				$scope.childrenDetails[1] = response.data;
				console.log($scope.childrenDetails[1]);
			});
		}
	});
	
	$scope.$watch("children[2]", function (newValue) {
		if(newValue) {
			$portal.getDetails($scope.children[2]).then(function (response) {
				$scope.childrenDetails[2] = response.data;
				console.log($scope.childrenDetails[2]);
			});
		}
	});

  
  /*
  //Test purpose
  $scope.log = function(item) {
	console.log(item);
  }
  */
  
  //Children Function
  $scope.setChildren = function(itemId) {
	$scope.children[1] = itemId;
	$scope.doSearch("", 1, 1, itemId);
  }
  $scope.setChildrenSecond = function(itemId) {
	$scope.children[2] = itemId;
	$scope.doSearch("", 1, 2, itemId);
  }
  $scope.setChildrenThird = function(itemId) {
	$scope.setChildren($scope.children[2]);
	$scope.setChildrenSecond(itemId);
  }
  
  //Target && basket Functions
  //Push to basket
  $scope.pushIt = function(item) {
	if(item.id != $scope.target.id)
	{
		$scope.basket.push(item);
	}
  }
  //Make target
  $scope.targetIt = function(item) {
		$scope.target = item;
  }
//**********************************************\\
  //Triger Search
  /*
	$scope.initiate = function() {
		if($scope.initiated != true)
		{
			console.log("test");
			$scope.initiated = true;
			$scope.doSearch("", 1, 0);
		}
	}
	$scope.initiate();
	*/

}