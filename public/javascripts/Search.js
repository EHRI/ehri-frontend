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
			var filtered = [];
			angular.forEach(descriptions, function (description) {
				// console.log(description);
				if(description.data.languageCode == lang)
				{
					// console.log("Language option match description language")
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
	}).filter('extraHeader', function() {
		return function(extras) {
			var filtered = [];
			angular.forEach(extras, function(extra, key) {
				switch(key)
				{
					case "datesOfExistence":
					// case "typeOfEntity":
					case "parallelFormsOfName":
					case "place":
						filtered.push({'content': extra, 'key':key});
						break;
				}
				// console.log(key);
			});
			// console.log("Nothing");
			
			if(filtered.length > 0)
			{
				// console.log(filtered.length);
				return filtered;
			}
		}
	});

function SearchCtrl($scope, $http) {
	//Tests function, including mockup
	$scope.initiate = function () { 
		$http.get(ANGULAR_ROOT + '/search/mock-results.json').success(function(data) {
			// console.log(data);
			$scope.page = data.page;
			// console.log($scope.page);
		});
	}
	$scope.initiate();
	
	
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