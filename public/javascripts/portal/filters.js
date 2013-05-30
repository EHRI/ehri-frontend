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