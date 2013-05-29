var portal = angular.module('portalSearch', ['ui.bootstrap' ], function ($provide) {
    $provide.factory('$service', function() {
      return {
        redirectUrl: function(type, id) {
          return jsRoutes.controllers.Application.getType(type, id).url;
        }
      };
    });
  }).
	config(['$routeProvider', function($routeProvider) {
	$routeProvider.
		when('/', {templateUrl: ANGULAR_ROOT + '/search/searchlikeg.html', controller: SearchCtrl, reloadOnSearch: false}).
		//when('/:searchTerm', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		/*
		when('/:searchTerm', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm/:lang/', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm/:lang/:type', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		*/
		otherwise({redirectTo: '/'});
}]);


//Filters
portal
	.filter('descLang', function() {
		return function(descriptions, lang) {	
			if(descriptions)
			{
				var filtered = [];
				angular.forEach(descriptions, function (description) {
					if(description.data.languageCode == lang)
					{
						filtered[0] = description;
					}
				});
				if(filtered[0])
				{
					return filtered;
				}
				else
				{
					return descriptions;
				}
			}
		}
	}).filter('noButtonFilter', function () {
		return function(array) {
			var response = {};
			angular.forEach(array, function(v,k) {
				if(k != 'q' && k != 'sort' && k != 'page')
				{
					response[k] = v;
				}
			});
			return response;
		}
	}).filter('extraHeader', function() {
		function extraHeader(extras) {
				
				var filtered = [];
				
				angular.forEach(extras, function(extra, key) {
					switch(key)
					{
						case "datesOfExistence":
						case "parallelFormsOfName":
						case "place":
							filtered.push({'content': extra, 'key':key})
							break;
					}
				});
				
				if(filtered.length > 0)
				{
					return filtered;
				}
				return false;
		}
		
		function defineHashKeys(array) {
			for (var i=0; i<array.length; i++) {
				array[i].$$hashKey = i;
			}
		}

		return function(array, chunkSize) {
			var result = extraHeader(array);
			defineHashKeys(result);
			return result;
		}
	});

portal.directive('whenScrolled', function ($window) {
    return function(scope, element, attrs) {
        angular.element($window).bind("scroll", function() {
		
			var bottomPos = parseInt(element[0].offsetTop) + parseInt(element[0].offsetHeight) + 95;
			if (document.documentElement.scrollTop) { var currentScroll = document.documentElement.scrollTop; } else { var currentScroll = document.body.scrollTop; }
			var totalHeight = document.body.offsetHeight;
			var visibleHeight = document.documentElement.clientHeight;
			
			// Particularities : we have margin on top and bot
			//Bottom = 65
			//Top = 95
			
			//Total height of doc = (currentScroll + visibleHeight + 150)
			//Div's Bottom position = (element[0].offsetTop + element[0].offsetHeight)
			//Take care of calling it in angular.element. In any other case you would have its height before datas has been loaded
			
			var currentScrollHeight = currentScroll + visibleHeight + 95;
             if (bottomPos  <= currentScrollHeight) {
				scope.$apply(attrs.whenScrolled);
             }
        });
    }
});
	
	
function SearchCtrl($scope, $http, $routeParams, $location, $service) {
	//Scope var
	$scope.searchParams = {'page' : 1, 'sort' : 'score.desc'};
	$scope.langFilter = "en";
	
	
	$scope.currentPage = 1; //Current page of pagination
	$scope.justLoaded = false;
	$scope.maxSize = 10; // Number of pagination buttons shown
	$scope.numPages = false; // Number of pages (get from query)
	
	$scope.lastFilter = false;
	$scope.loadingPage = false;
	
	
	$scope.fromSearch = function() {
		angular.forEach($location.search(), function(value, key) {
			$scope.searchParams[key] = value;
			if(key == "q")
			{
				$scope.searchTerm = value;
			}
			else if(key == "page")
			{
				$scope.currentPage = parseInt(value);
			}
		});
	}
	
	//Query functions
	$scope.setQuery = function(type, q) {
		if($scope.searchParams[type] == q && type != 'page' && type != 'q' && type != 'sort')
		{
			$scope.removeFilterByKey(type);
			$location.search('page', 1);
		}
		else
		{
			if(type != 'page')
			{
				$location.search('page', null);
				delete $scope.searchParams.page;
			}
			else {
				q = parseInt(q);
			}
			$scope.lastFilter = type;
			$scope.searchParams[type] = q;
			$location.search(type, q);
			
			$scope.doSearch();
		}
	}
	
	$scope.getUrl = function(url) {			
		url = url + '?';
		
		var urlArr = [];
		angular.forEach($scope.searchParams, function(value, key) {
			urlArr.push(key + "=" + value);
		});
		
		return url + urlArr.join('&');
	}
	
	$scope.doSearch = function(push) {				
		url = $scope.getUrl('/search');
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			if(push)
			{
				angular.forEach(data.page.items, function(value){
					$scope.items.push(value);
				});
				$scope.facets = data.facets;
			}
			else
			{
				$scope.page = data.page;
				$scope.items = data.page.items;
				$scope.facets = data.facets;
				$scope.numPages = data.numPages;
			}
			// console.log($scope.items);
			//Get the loading possible again
			$scope.loadingPage = false;
		}).error(function() { 
			$scope.removeFilterByKey($scope.lastFilter);
			alert('Server error, reloading datas');
		});
	}
	
	$scope.loadMore = function () {
		if($scope.loadingPage == false && $scope.currentPage != $scope.numPages)
		{
			$scope.loadingPage = true;
			$scope.currentPage = $scope.currentPage + 1;
			$scope.searchParams.page = $scope.currentPage;
			$scope.doSearch(true);
			console.log("ScrolltoCall for page " + $scope.currentPage);
		}
	}
	
	$scope.removeFilterByKey = function(key){
		delete $scope.searchParams[key];
		$location.search(key, null);
		$scope.doSearch();
		return true;
	}
	
	$scope.removeFilter = function (value) {
		angular.forEach($scope.searchParams, function(v,k) {
			if(v == value)
			{
				delete $scope.searchParams[k];
			}
		});
		angular.forEach($location.search(), function(v, k) {
			if(v == value)
			{
				$location.search(k, null);
			}
		});
		$scope.doSearch();
		return true;
	}
	
	$scope.getLink = function(type,id) {
		console.log(type);
		console.log($service.redirectUrl(type, id));
        location.href = $service.redirectUrl(type, id);
	}
	
/**********
**
**
**
**
*/
	//Description functions	
	$scope.getTitleAction = function(item) {
		//2013-05-15 15:42:23 Mike Bryant: Imported from command-line
		event = item.relationships.lifecycleEvent[0];
		message = event.data.logMessage;
		return message;
	}
	
	$scope.getSimpleTimeDesc = function(item) {
		//Updated 5 days ago
		event = item.relationships.lifecycleEvent[0];
		//d = new Date(event.data.timestamp);
		return event.data.timestamp;
	}
	
	$scope.fromSearch();
	$scope.doSearch();
}