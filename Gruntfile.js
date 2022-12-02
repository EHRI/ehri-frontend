module.exports = function (grunt) {

	grunt.loadNpmTasks('grunt-contrib-copy');

  const paths = {
    portalJsLib: "modules/portal/app/assets/js/lib",
    portalCssLib: "modules/portal/app/assets/css/lib",
    portalFonts: "modules/portal/public/fonts",
    adminJsLib: "modules/admin/app/assets/js/lib",
    adminCssLib: "modules/admin/app/assets/css/lib",
  };

  // Since Git will replace CRLF with LF anyway we do it here
  // so copying from Grunt doesn't result in modified files.
	function normEndings(contents) {
    return contents.replace(/\r\n/g, "\n");
  }

	grunt.initConfig({

    // Copy asset files from NPM to the src folder. This should
    // be done after installing or updating asset packages.
    copy: {
      main: {
        options: {
          process: function(contents, srcpath) {
            // One small tweak to replace the font path in font-awesome.
            // TODO: find a way to avoid this
            if (srcpath === "node_modules/font-awesome/scss/_variables.scss") {
              return normEndings(contents.replace(/"\.\.\/fonts"/, '"/v/fonts"'));
            }

            // return normEndings(content);
            return normEndings(contents);
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
            dest: paths.portalCssLib + '/bootstrap'
          },
          {
            expand: true,
            cwd: 'node_modules/select2/dist/js',
            src: 'select2.full.js',
            dest: paths.portalJsLib
          },
          {
            expand: true,
            cwd: 'node_modules/select2/src/scss',
            src: '**/*.scss',
            dest: paths.portalCssLib + '/select2'
          },
          {
            expand: true,
            cwd: 'node_modules/@ttskch/select2-bootstrap4-theme/src',
            src: '_*.scss',
            dest: paths.portalCssLib + '/select2-bootstrap'
          },
          {
            expand: true,
            cwd: 'node_modules/material-design-icons/iconfont',
            src: '**/*.{ttf,woff,woff2,eot,svg}',
            dest: paths.portalFonts
          },
          {
            expand: true,
            cwd: 'node_modules/font-awesome/fonts',
            src: '**/*.{ttf,woff,woff2,eot,svg}',
            dest: paths.portalFonts
          },
          {
            expand: true,
            cwd: 'node_modules/font-awesome/scss',
            src: '*.scss',
            dest: paths.portalCssLib + '/font-awesome',
          },
          {
            expand: true,
            cwd: 'node_modules/jquery/dist',
            src: 'jquery.js',
            dest: paths.portalJsLib
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
          {
            expand: true,
            cwd: 'node_modules/js-cookie/src',
            src: 'js.cookie.js',
            dest: paths.portalJsLib
          },
          // Admin JS modules
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
            dest: paths.adminCssLib
          },
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
            src: [
              'vue.global.js',
              'vue.runtime.global.js'
            ],
            dest: paths.adminJsLib
          },
        ],
      },
    },
  });

	grunt.registerTask('copy-assets', [
		'copy'
	]);

    grunt.registerTask('default', [
        'copy-assets'
    ]);
};
