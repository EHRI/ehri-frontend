var Doc = portal.controller('DocCtrl', ['$scope', '$filter', '$location', '$routeParams', '$http', 'Item', function($scope, $filter, $location, $routeParams, $http, $item) {
	$scope.blocks = {};
	$scope.item = $item.data;
	$scope.alt = {};
	$scope.compared = {};
	
	
	
//-------------------------------------URL AND ADD/DEL DESC
	$scope.urlParams = {};
	
	//<-- Set id of desc
	if($location.search().description) {
		$scope.descId = $location.search().description;
	}
	$scope.$on('$routeUpdate', function(){
		$scope.fromUrl();
	});
	// Set id of desc -->
	
	$scope.fromUrl = function() {
		$scope.urlParams = $location.search();
		console.log(typeof $location.search().compared );
		if(typeof $location.search().compared == "string") {
			$scope.urlParams.compared = $scope.urlParams.compared.split(",");
		}
		if($location.search().description) {
			$scope.descId = $location.search().description;
			$scope.loadDesc();// Change desc
		}
		if ($location.search().compared) {
			$scope.compared = {};
			angular.forEach($scope.urlParams.compared, function(value, key) {
				$scope.compareWith(value);
			});
		}
	}
	
	
	$scope.setSearch = function () {
		$location.search($scope.urlParams);
	}
	
	$scope.resetCompared = function() {
		$scope.urlParams.compared = [];
		angular.forEach($scope.compared, function(value, key) {
			$scope.urlParams.compared.push(key);
		});
		$scope.setSearch();
	}
	
	$scope.setDesc = function(descId) {
		$scope.urlParams.description = descId;
		$scope.setSearch();
	}
	
	$scope.removeDesc = function (descId) {
		delete $scope.compared[descId];
		$scope.resetCompared();
	}
//-------------------------------------URL AND ADD/DEL DESC
	
	//Color for compared desc
	$scope.descNbr = 0;
	$scope.getColor = function () {
		var available = ['deepblue', 'green', 'purple', 'alizarin']
		if($scope.descNbr  <= 3) {
			$scope.descNbr = $scope.descNbr + 1;
		} else {
			$scope.descNbr = 0;
		}
		return available[$scope.descNbr - 1];
	}
	
	$scope.closeAll = function () {
		angular.forEach($scope.blocks, function(value, key){
			$scope.blocks[key].closed = true;
		});
	}
	$scope.hideAll = function () {
		angular.forEach($scope.blocks, function(value, key){
			$scope.blocks[key].hidden = true;
		});
	}
	
	$scope.compareWith = function(itemId) {
		$http.get('./api/documentaryUnit/'+itemId).success(function(data) {
			$scope.compared[itemId] = data;
			$scope.compared[itemId].color = $scope.getColor();
			if(!$scope.compared[itemId]) { $scope.resetCompared(); }
		});
	}
	
	//<-- Select good desc 
	$scope.loadDesc = function() {
		if($scope.descId)
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
	$scope.fromUrl();
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