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
		when('/', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl, reloadOnSearch: false}).
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
			// console.log(descriptions);			
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
					// console.log(filtered.data);
					return filtered;
				}
				else
				{
					// filtered[0] = descriptions;
					console.log(filtered);
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

function SearchCtrl($scope, $http, $routeParams, $location, $service) {
	//Scope var
	$scope.searchParams = {};
	$scope.langFilter = "en";
	
	$scope.searchParams['sort'] = 'score.desc';
	
	$scope.currentPage = 1; //Current page of pagination
	$scope.justLoaded = false;
	$scope.maxSize = 10; // Number of pagination buttons shown
	$scope.numPages = false; // Number of pages (get from query)
	
	$scope.lastFilter = false;
	
	//Watch for page click
	$scope.$watch("currentPage", function (newValue) {
		if($scope.justLoaded == false)
		{
			$scope.justLoaded = true;
		}
		else
		{
			$scope.setQuery("page", newValue);
		}
	});
	
	
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
				//console.log($scope.currentPage);
			}
		});
	}
	
	//Query functions
	$scope.setQuery = function(type, q) {
		if($scope.searchParams[type] == q && type != 'page' && type != 'q' && type != 'sort')
		{
			$scope.removeFilterByKey(type);
			$location.search('page', null);
		}
		else
		{
			if(type != 'page')
			{
				$location.search('page', null);
				delete $scope.searchParams.page;
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
	
	$scope.doSearch = function() {				
		url = $scope.getUrl('/search');
		// console.log("Url for Query : " + url);
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			$scope.page = data.page;
			$scope.facets = data.facets;
			$scope.numPages = data.numPages;
		}).error(function() { 
			$scope.removeFilterByKey($scope.lastFilter);
			alert('Server error, reloading datas');
		});
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