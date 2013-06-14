portal
	.filter('descLang', function() {
		return function(descriptions, lang, opt) {	
		
			//<-- Options
			//Schema of option available
			var optionnalScheme = {"id" : false, "not": false, "property" : false, "returnProp" : false}
			//Merge with requested options
			for (var attrname in opt) { optionnalScheme[attrname] = opt[attrname]; }
			//Reset as opt
			var opt = optionnalScheme;
			// Options -->
			//console.log(opt);
			//<-- Function to get Value from a string as path
			var deepFind = function (obj, path) {
				//console.log("path = " + path);
				var paths = path.split('.')
					, current = obj
					, i;

				for (i = 0; i < paths.length; ++i) {
					if (current[paths[i]] == undefined) {
						return undefined;
					} else {
						current = current[paths[i]];
					}
				}
				return current;
			};
			// Function to get Value from a string as path -->
			
			//If we got descriptions
			if(descriptions)
			{
				var filtered = [];
				var i = parseInt(0);
				angular.forEach(descriptions, function (description) {
					if(!filtered[0]) // Would break the foreach if we found an answer
					{
					++i;
					// console.log(i);
							//If ask for a certain property
						if(opt.property)
						{
							var deepVal = deepFind(description, opt.property);
							// console.log(opt);
							// console.log(deepVal);
						} else {
							// console.log(opt);
							// console.log("Deep val to undefined");
							var deepVal = undefined;
						}
						
						if((opt.property) && (deepVal != undefined) && (opt.not) && (description.id != opt.not)) {
							// console.log("not called");
							// console.log(opt.returnProp);
							if(opt.returnProp) {
								filtered[0] = {"text" : deepVal, "id" : description.id, "name" : description.data.name};
							} else {
								filtered[0] = description;
							}
							// console.log("Filtered[0] :");
							// console.log(filtered[0]);
							//If ask for not a certain id
						} else if((opt.property) && (deepVal != undefined) && (!opt.not)) {
							// console.log("not not called");
							// console.log(opt.returnProp);
							if(opt.returnProp) {
								filtered[0] = {"text" : deepVal, "id" : description.id, "name" : description.data.name};
							} else {
								filtered[0] = description;
							}
							//If ask for not a certain id
						}  else if ((opt.not) && (description.id != opt.id)) {
							//if lang has been asked
							if((lang) && (description.data.languageCode == lang)) {
								filtered[0] = description;
							} 
							else if (!lang)
							{
								filtered[0] = description;
							}
							
							//if ask for a certain id
						} else if ((opt.id) && (description.id == opt.id)) {
							filtered[0] = description;
							
							//Last option is definitly lang
						} else if((lang) && (description.data.languageCode == lang)) {
							filtered[0] = description;
						} 
					}
				});
				// console.log("Filtered[0] :");
				// console.log(filtered[0]);
				if(filtered[0])
				{
					// console.log(filtered);
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