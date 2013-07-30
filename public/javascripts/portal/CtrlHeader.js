var header = portal.controller('headerCtrl', ['$scope', '$location', 'myBasketService', function($scope, $location, $basket) {

	$scope.ui = {
		basket : {
			size: {
				get: function() {
					$scope.ui.basket.array = [];
					angular.forEach($scope.ui.basket.raw, function(val) {
						$scope.ui.basket.array.push(val);
					});
					$scope.ui.basket.size.val = $scope.ui.basket.array.length;
				},
				val: 0
			},
			raw : {},
			array : []
		},
		search : {	//Quick Search Module
			params: {},
			advanced : function (input) {	//Parse the query for advanced use of research
				var regexp = {
					type : /TYPE\(([A-z]+)\)/,
					sort : /SORT\(([A-z]+)\)/
				}
				angular.forEach(regexp, function(value, key) {
					r = new RegExp(value);
					results = r.exec(input);
					if(results != null) {
						$scope.ui.search.filter.set(key, results[1]);
					}
				});
			},
			input : function () {	//Function triggered on ng-change for  searchTerm
				this.params.q = $scope.searchTerm;	
			},
			filter : {
				set : function(key, value) { // Change a filter
					$scope.ui.search.params[key] = value;	//Set param
				}			
			},
			submit: function() {
				this.advanced();
				this.input();
				url = this.url();
				$location.url(url);
			},
			url: function () {			
				url = 'search?';	//Add question mark
				var urlArr = [];
				angular.forEach($scope.ui.search.params, function(value, key) {
					urlArr.push(key + "=" + value);
				});
				
				return url + urlArr.join('&');
			}
		},
		url : {	//Url for browser functions
			set : function () { // Set search params in Url
				$location.search($scope.ui.search.params);
			}
		}
	}
	//Check for basket event
    $scope.$on('ui.basket.get', function() {
        $scope.ui.basket.raw[$basket.content.transit.id] = $basket.content.transit.id;	// Dont need more data than id
		$scope.ui.basket.size.get();
    });
	
}]);