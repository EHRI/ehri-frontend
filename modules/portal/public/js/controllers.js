
function SearchCtrl($scope, $rootScope, $search, $location, $http) {
  console.log("Loading searchctrl")

  function filterParams(params) {
    var filtered = {};
    for (key in params) {
      if (params.hasOwnProperty(key)) {
        if (params[key] !== undefined && params[key] != null && params[key] != "") {
          filtered[key] = params[key] + "";
        }
      }
    }
    return filtered;
  }

  $scope.nextPage = function() {
    $scope.searchParams.page++;
    $scope.doSearch();
  }

  $scope.prevPage = function() {
    $scope.searchParams.page = Math.max(1, $scope.searchParams.page - 1);
    $scope.doSearch();
  }

  $scope.isSelectedType = function(type) {
    return $scope.searchParams.st.indexOf(type) != -1;
  }

  $scope.addTypeFilter = function(type) {
    $scope.searchParams.st.push(type);
    $scope.doSearch();
  }

  $scope.removeTypeFilter = function(type) {
    $scope.searchParams.st.splice($scope.searchParams.st.indexOf(type), 1);
    $scope.doSearch();
  }

  $scope.doSearch = function() {
    console.log("Setting search: ", filterParams($scope.searchParams))
    // Setting location causes problems!
    //$location.search(filterParams($scope.searchParams));
    //$location.search({st: undefined})
    $http({url: getSearchUrl(), method: "GET", headers:{Accept: "application/json"}}).success(function(data) {
      $scope.results = data;
    });
  };

  $scope.searchParams = {
    q: $location.search().q || "",
    page: parseInt($location.search().page, 10) || 1,
    excludes: [],
    facets: [],
    st: []
  };


  var getSearchUrl = function() {
    var url = jsRoutes.controllers.portal.Application.search().url;
    url += "?q=" + $scope.searchParams.q.trim();
    if ($scope.searchParams.page > 1) {
      url += "&page=" + $scope.searchParams.page;
    }
    if ($scope.searchParams.st.length > 0) {
      for (i in $scope.searchParams.st) {
        url += "&st[]=" + $scope.searchParams.st[i];
      }
    }
    console.log(url)
    return url;
  }

  $scope.results = {};

  $scope.doSearch();
}

function ProfileCtrl($scope, $rootScope, $http) {

}

function ItemCtrl($scope, $rootScope, $routeParams, $http) {

  var url = null;
  switch ($routeParams.itemType) {
    case EntityTypes.documentaryUnit:
      url = jsRoutes.controllers.archdesc.DocumentaryUnits.get($routeParams.itemId).url;
      break;
    case EntityTypes.historicalAgent:
      url = jsRoutes.controllers.authorities.HistoricalAgents.get($routeParams.itemId).url;
      break;
    case EntityTypes.repository:
      url = jsRoutes.controllers.archdesc.Repositories.get($routeParams.itemId).url;
      break;
    case EntityTypes.cvocConcept:
      url = jsRoutes.controllers.vocabs.Concepts.get($routeParams.itemId).url;
      break;
    default: url = "none";
  }
  //$http.get(url).success(function(data) {
  //$http({method: "GET", url: url, headers: {Accept: "application/json"}}).success(function(data) {
  $http.get(jsRoutes.controllers.core.Application.getGeneric($routeParams.itemId).url).success(function(data) {
    console.log(data)
    $scope.itemData = data;
  });
}