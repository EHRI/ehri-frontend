var portal = angular.module('portalSearch', []).
	config(['$routeProvider', function($routeProvider) {
	$routeProvider.
		when('/', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm/:lang/', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm/:lang/:type', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
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
					filtered[0] = descriptions[0];
					return descriptions[0];
				}
			}
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
			// console.log(result);
			return result;
		}
	});

function SearchCtrl($scope, $http) {
	$scope.searchParams = [];
	
	$scope.setQuery = function(type, q) {
		$scope.searchParams[type] = q;
		
		console.log($scope.searchParams);
		
		$scope.doSearch();
	}
	
	$scope.getUrl = function(url) {	
		var param = $scope.searchParams;
		console.log(param);
		
		console.log("Element in searchParams : " + param.length);
		
		// if(param.length > 0)
		// {
			url = url + '?';
			
			var urlArr = [];
			angular.forEach(param, function(value, key) {
				console.log(value);
				urlArr.push(key + "=" + value);
			});
			
			return url + urlArr.join('&');
		// }
		return url;
		
	}
	
	$scope.doSearch = function() {		
		console.log($scope.searchParams);
		
		url = $scope.getUrl('/search');
		
		console.log("Url for Query : " + url);
		
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			// console.log(data);
			$scope.page = data.page;
			// console.log($scope.page);
		});
	}
	
	
	
	//Tests function, including mockup
	$scope.initiate = function () { 
		/*
		$http.get('/search').success(function(data) {
			// console.log(data);
			$scope.page = data.page;
			// console.log($scope.page);
		});
		*/
	}
	$scope.doSearch();
	
	
	
	//Description functions
	$scope.langFilter = "en";
	
	$scope.extraDataParser = function (item) {
		temp = {};
		// console.log(item);
		// return item.relationships.describes[0].data;
		return false;
	}
	
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
	
}




//"Accept: application/json" "http://10.88.12.4:9000/search?q=hitler"