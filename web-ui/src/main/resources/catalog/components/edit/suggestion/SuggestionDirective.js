(function() {
  goog.provide('gn_suggestion_directive');

  /**
   * Provide directives for suggestions of the
   * edited metadata.
   *
   * - gnSuggestionList
   */
  angular.module('gn_suggestion_directive', [])
  .directive('gnSuggestionList', ['gnSuggestion', 'gnCurrentEdit',
        function(gnSuggestion, gnCurrentEdit) {
          return {
            restrict: 'A',
            templateUrl: '../../catalog/components/edit/suggestion/' +
                'partials/list.html',
            scope: {},
            link: function(scope, element, attrs) {
              scope.gnSuggestion = gnSuggestion;
              scope.gnCurrentEdit = gnCurrentEdit;

              var init = function() {
                gnSuggestion.load().success(function(data) {
                  if(data && !angular.isString(data)) {
                    scope.suggestions = data;
                  }
                  else {
                    scope.suggestions = [];
                  }
                });
              };

              // When saving is done, refresh validation report
              scope.$watch('gnCurrentEdit.saving', function(newValue) {
                if (newValue === false) {
                  init();
                }
              });
            }
          };
        }])
  .directive('gnRunSuggestion', ['gnSuggestion',
        function(gnSuggestion) {
          return {
            restrict: 'A',
            templateUrl: '../../catalog/components/edit/suggestion/' +
                'partials/runprocess.html',
            link: function(scope, element, attrs) {
              scope.gnSuggestion = gnSuggestion;

              /**
               * Init form parameters.
               * This function is registered to be called on each
               * suggestion click in the suggestions list.
               */
              var initParams = function() {
                scope.params = {};
                var p = gnSuggestion.getCurrent().params;
                for (key in p) {
                  scope.params[key] = p[key].defaultValue;
                }
              };
              gnSuggestion.register(initParams);

            }
          };
        }]);
})();
