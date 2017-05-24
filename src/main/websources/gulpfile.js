'use strict';

const gulp = require('gulp');
const watch = require('gulp-watch');
const sequence = require('run-sequence');
const util = require('gulp-util')
const minifyCss = require('gulp-clean-css');
const minifyJs = require('gulp-uglify');
const jshint = require('gulp-jshint');
const sass = require('gulp-sass');
const sourceMaps = require('gulp-sourcemaps');
const del = require('del');
const jasmineBrowser = require('gulp-jasmine-browser');
const gulpif = require('gulp-if');

const config = {
    target: "../../../target/classes/web",
    testTarget: "../../../target/test-classes/web",
    ckeditor_version: "4.6.0",
    production: !!util.env.production
};

const TARGET = config.target;

const fileForTest = [
    config.testTarget + '/jquery/jquery.min.js',
    config.testTarget + '/jquery/plugins/jquery.form.js',
    config.testTarget + '/js/frameworks/es5-shim-dejavu.js',
    config.testTarget + '/js/view-dropdown.js',
    config.testTarget + '/js/**/*.js',
    'node_modules/jasmine-ajax/lib/mock-ajax.js',
    'test/fixtures/**/*.js',
    'test/**/*_test.js'
];

function errorHandler(e) {
    util.log(e);
    if (config.production) {
        process.exit(1);
    }
}

gulp.task('theme-compile-sass', function () {
    return gulp.src('themes/default/scss/*.scss')
        .pipe(gulpif(!config.production, sourceMaps.init()))
        .pipe(sass().on('error', sass.logError))
        .pipe(gulpif(config.production, minifyCss()))
        .pipe(gulpif(!config.production, sourceMaps.write()))
        .pipe(gulp.dest(TARGET + '/themes/default'));
});

gulp.task('theme-compile-editor-structured-resources-sass', function () {
    return gulp.src('themes/default/scss/structured-resources/*.scss')
        .pipe(gulpif(!config.production, sourceMaps.init()))
        .pipe(sass().on('error', sass.logError))
        .pipe(gulpif(config.production, minifyCss()))
        .pipe(gulpif(!config.production, sourceMaps.write()))
        .pipe(gulp.dest(TARGET + '/themes/default/structured-resources'));
});

gulp.task('theme-copy-css', function () {
    return gulp.src(['themes/**/*.css'])
        .pipe(gulpif(config.production, minifyCss({inline: ['none']})))
        .pipe(gulp.dest(TARGET + '/themes'));
});

gulp.task('theme-copy-resources', function () {
    return gulp.src(['themes/**', '!themes/**/scss/*.scss', '!themes/**/*.css'])
        .pipe(gulp.dest(TARGET + '/themes'));
});

gulp.task('jquery-copy', function () {
    return gulp.src('jquery/**')
        .pipe(gulp.dest(TARGET + "/jquery"));
});

gulp.task('jquery-plugins-copy', function () {
    return gulp.src('jquery/plugins/**')
        .pipe(gulp.dest(TARGET + "/jquery/plugins"));
});

gulp.task('flash-copy', function () {
    return gulp.src('flash/**')
        .pipe(gulp.dest(TARGET + "/flash"));
});

gulp.task('js-copy', function () {
    return gulp.src('js/**/*.js')
        .pipe((config.production) ? minifyJs({keep_fnames: true}) : util.noop())
        .on('error', errorHandler)
        .pipe(gulp.dest(TARGET + "/js"));
});

gulp.task('js-copy-resources', function () {
    return gulp.src(['js/**', '!js/**/*.js'])
        .pipe(gulp.dest(TARGET + "/js"));
});

gulp.task('ckeditor-org', function () {
    return gulp.src('CKEditor/ckeditor-org/ckeditor-' + config.ckeditor_version + '/**')
        .pipe(gulp.dest(TARGET + '/ckeditor-build'));
});

gulp.task('ckeditor-copy', ['ckeditor-org'],  function () {
    return gulp.src('CKEditor/ckeditor-modifications/**')
        .pipe(gulp.dest(TARGET + '/ckeditor-build'));
});

gulp.task('watch', function () {
    gulp.watch('themes/default/scss/*.scss', ['theme-compile-sass']);
    gulp.watch('themes/default/scss/structured-resources/*.scss', ['theme-compile-editor-structured-resources-sass']);
    gulp.watch('themes/**/*.css', ['theme-copy-css'])
    gulp.watch('js/**/*.js', ['js-copy']);
    gulp.watch('jquery/**/*.js', ['jquery-plugins-copy', 'jquery-copy']);
    gulp.watch('CKEditor/ckeditor-modifications/**', ['ckeditor-copy']);
});

gulp.task('clean', function () {
    return del(TARGET, {force: true});
});

gulp.task('test-dependencies', function (callback) {
    TARGET = config.testTarget;
    return sequence(
        'clean',
         [
            'js-copy',
            'js-copy-resources',
            'jquery-plugins-copy',
            'jquery-copy',
            'ckeditor-copy'
         ],
         callback
    );
});

gulp.task('test', ['test-dependencies'], function () {
    return gulp.src(fileForTest)
        .pipe(jasmineBrowser.specRunner({console: true}))
        .pipe(jasmineBrowser.headless());
});

gulp.task('test-browser', ['test-dependencies'], function () {
    gulp.watch('themes/**/*.css', ['theme-copy-css'])
    gulp.watch('js/**/*.js', ['js-copy']);
    return gulp.src(fileForTest)
        .pipe(watch(fileForTest))
        .pipe(jasmineBrowser.specRunner())
        .pipe(jasmineBrowser.server());
});

gulp.task('default', function () {
    return gulp.start(
        'theme-compile-sass',
        'theme-compile-editor-structured-resources-sass',
        'theme-copy-css',
        'theme-copy-resources',
        'js-copy',
        'js-copy-resources',
        'jquery-plugins-copy',
        'jquery-copy',
        'ckeditor-copy',
        'flash-copy'
    );
});
