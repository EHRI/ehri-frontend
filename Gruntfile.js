module.exports = function (grunt) {

	grunt.loadNpmTasks('grunt-contrib-copy');

	var paths = {
    portalJsLib: "modules/portal/app/assets/js/lib",
	  portalCss: "modules/portal/app/assets/css",
    adminJsLib: "modules/admin/app/assets/js/lib",
    adminCss: "modules/admin/app/assets/css",
  };

	grunt.initConfig({

    // Copy asset files from NPM to the src folder. This should
    // be done after installing or updating asset packages.
    copy: {
      main: {
        options: {
          process: function(contents, srcpath) {
            if (srcpath === "node_modules/font-awesome/scss/_variables.scss") {
              return contents.replace(/"\.\.\/fonts"/, '"/v/fonts"');
            }

            console.log(srcpath);

            return contents;
          },

          noProcess: [
            '**/*.{png,gif,jpg,ico,psd,ttf,otf,woff,woff2,eot,svg}'
          ]
        },
        files: [
          {
            expand: true,
            cwd: 'node_modules/bootstrap/scss',
            src: '**/*.scss',
            dest: 'modules/portal/app/assets/css/bootstrap/'
          },
          {
            expand: true,
            cwd: 'node_modules/select2/src/scss',
            src: '**/*.scss',
            dest: 'modules/portal/app/assets/css/select2/'
          },
          {
            expand: true,
            cwd: 'node_modules/font-awesome/fonts',
            src: '**/*.{ttf,woff,woff2,eot,svg}',
            dest: 'modules/portal/public/fonts'
          },
          {
            expand: true,
            cwd: 'node_modules/font-awesome/scss',
            src: '_*.scss',
            dest: 'modules/portal/app/assets/css/fontawesome/',
          },

          // Portal JS modules... these are uglified by SBT so we
          // don't use the .min.js versions
          {
            expand: true,
            cwd: 'node_modules/jquery/dist',
            src: 'jquery.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/bootstrap-datepicker/dist/js',
            src: 'bootstrap-datepicker.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/bootstrap-datepicker/dist/css',
            src: 'bootstrap-datepicker.standalone.css',
            dest: paths.adminCss
          },
          {
            expand: true,
            cwd: 'node_modules/bootstrap/dist/js',
            src: 'bootstrap.bundle.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/jquery-hoverintent',
            src: 'jquery.hoverIntent.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/jquery-placeholder',
            src: 'jquery.placeholder.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/jquery-validation/dist',
            src: 'jquery.validate.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/corejs-typeahead/dist',
            src: 'typeahead.bundle.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/handlebars/dist',
            src: 'handlebars.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/clipboard/dist',
            src: 'clipboard.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/leaflet/dist',
            src: ['leaflet.js', 'leaflet.css', 'images/*'],
            dest: paths.portalJsLib + '/leaflet'
          },
          {
            expand: true,
            cwd: 'node_modules/leaflet-curve',
            src: 'leaflet.curve.js',
            dest: paths.portalJsLib
          },
          // Admin JS modules
          {
            expand: true,
            flatten: true,
            cwd: 'node_modules/codemirror',
            src: [
              'lib/codemirror.js',
              'lib/codemirror.css',
              'mode/cypher/cypher.js',
              'mode/javascript/javascript.js',
              'mode/xml/xml.js'
            ],
            dest: paths.portalJsLib + '/codemirror'
          },
          {
            expand: true,
            cwd: 'node_modules/luxon/build/global',
            src: 'luxon.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/lodash',
            src: 'lodash.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/axios/dist',
            src: 'axios.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/jquery-flot',
            src: ['jquery.flot.js', 'jquery.flot.pie.js'],
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/vue/dist',
            src: 'vue.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/sortablejs',
            src: 'Sortable.js',
            dest: paths.adminJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/vuedraggable/dist',
            src: 'vuedraggable.umd.js',
            dest: paths.adminJsLib
          },
        ],
      },
    },
  });

	// Copy JS assets
	grunt.registerTask('copy-assets', [
		'copy'
	]);
};
