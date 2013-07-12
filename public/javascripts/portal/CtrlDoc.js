var Doc = portal.controller('DocCtrl', ['$scope', '$filter', '$location', '$routeParams', '$http', 'Item', function($scope, $filter, $location, $routeParams, $http, $item) {
	$scope.blocks = {};
	$scope.item = $item.data;
	$scope.alt = {};
	$scope.compared = {};
	//<-- Set id of desc
	if($location.search().description) {
		$scope.descId = $location.search().description;
	}
	$scope.$on('$routeUpdate', function(){
		if($location.search().description) {
			$scope.descId = $location.search().description;
			$scope.loadDesc();// Change desc
		} else if ($location.search().comparedWith) {
			if($scope.compared[$location.search().comparedWith])
			{
				$scope.loadDesc();// Change desc
			}
			else
			{
				$scope.compareWith($location.search().comparedWith, loadDesc);
			}
		}
	});
	// Set id of desc -->
	
	$scope.compareWith = function(itemId, load) {
		$http.get('./api/documentaryUnit/'+itemId).success(function(data) {
			console.log(data);
			$scope.compared[itemId] = data;
			console.log($scope.compared);
			if(load) { $scope.loadDesc(); }
		});
	}
	
	
	//<-- Select good desc 
	$scope.loadDesc = function() {
		if($location.search().comparedWith)
		{
			console.log("loadDesc");
			$scope.desc = $scope.compared[$location.search().comparedWith].relationships.describes[0];
			console.log($scope.desc);
		}
		else if($scope.descId)
		{
			$scope.desc = $filter("descLang")($scope.item.relationships.describes, false, {"id" : $scope.descId})[0];
			$scope.alt = [];
		}
		else
		{
			$scope.desc = $filter("descLang")($scope.item.relationships.describes)[0];
			$scope.alt = [];
		}
		//console.log($scope.desc);
		//$scope.$apply();
	}
	// Select good desc -->
	
	//<-- No Desc ? GOT ONE !
	$scope.getAlt = function (check, path, ret)
	{
		if($scope.desc != undefined) {
			// console.log("-----------------------");
			// console.log("Check : "+check);
			// console.log($scope.desc.data[check]);
			// console.log($scope.alt[path]);
			// console.log("-----------------------");
			if($scope.desc.data[check] == undefined && !$scope.alt[path]) {
				var alt = $filter("descLang")($scope.item.relationships.describes, false, {"not" : $scope.descId, "property": path, "returnProp" : ret});
				$scope.alt[path] = alt;
				return alt;
			}
			else {
				return $scope.alt[path];
			}
		}
	}
	// No desc -->
	
	//<-- Date format
	$scope.formatDate = function(dateMs) {
		var more = function(X) {
			if(X >= 10)	{
				return X;
			}
			else {
				return "0"+X;
			}
		}
		var date = new Date(dateMs);
		var month = date.getMonth()+1;
		var day = date.getDate();
		var year = date.getFullYear();
		return more(month) + '-' + more(day) + '-' + year;
	}
	// Date format -->
	
	//<-- Search Engine
	$scope.searchParams = {type: "score", order: "desc"};
	$scope.quickSearch = {};
	$scope.getUrl = function(url) {
		url = url + '?type=documentaryUnit&sort=' + $scope.searchParams.type + '.' + $scope.searchParams.order+ '&q=' + $scope.searchParams.q;
		
		return url;
	}
	
	$scope.doFilter = function(type, val) {
		$scope.searchParams[type] = val;
		$scope.doSearch();
	}
	
	$scope.doSearch = function() {	
		url = $scope.getUrl('/search');
		$http.get(url, {headers: {'Accept': "application/json"}}).success(function(data) {
				//Datas
				$scope.quickSearch = data.page.items;
			});
	}
	// Search Engine -->
	
	
	
	//<-- Load data 
	$scope.loadDesc();
	// Load data-->
}]);

Doc.resolveDoc = {
	itemData: function($route, Item) {
		console.log("Resolving in doc ???");
		var result = Item.query("documentaryUnit", $route.current.params.itemID);
		return result;
	},
	delay: function($q, $timeout) {
		var delay = $q.defer();
		$timeout(delay.resolve, 1000);
		return delay.promise;
	}
} 