var portal = angular.module('portalSearch', []).
	config(['$routeProvider', function($routeProvider) {
	$routeProvider.
		when('/', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
		when('/:searchTerm', {templateUrl: ANGULAR_ROOT + '/search/search.html', controller: SearchCtrl}).
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
					filtered[0] = descriptions[0];
					return descriptions[0];
				}
			}
		}
	}).filter('noButtonFilter', function () {
		return function(array) {
			var response = {};
			angular.forEach(array, function(v,k) {
				if(k != 'q' && k != 'sort')
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

function SearchCtrl($scope, $http) {
	//Scope var
	$scope.searchParams = {};
	$scope.langFilter = "en";
	
	if($scope.searchTerm)
	{
		console.log($scope.searchTerm);
	}
	
	//Query functions
	$scope.setQuery = function(type, q) {
		$scope.searchParams[type] = q;
		$scope.doSearch();
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
		console.log("Url for Query : " + url);
				$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
			$scope.page = data.page;
		});
	}
	
	$scope.removeFilter = function (value) {
		angular.forEach($scope.searchParams, function(v,k) {
			if(v == value)
			{
				delete $scope.searchParams[k];
			}
		});
		$scope.doSearch();
		return true;
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
	
	$scope.doSearch();
}