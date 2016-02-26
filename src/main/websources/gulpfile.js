'use strict';

var gulp = require('gulp');
var util = require('gulp-util')
var minifyCss = require('gulp-clean-css');
var minifyJs = require('gulp-uglify');
var jshint = require('gulp-jshint');
var concat = require('gulp-concat');
var concatBower = require('gulp-concat-vendor');
var mainBowerFiles = require('main-bower-files');
var sass = require('gulp-sass');
var sourceMaps = require('gulp-sourcemaps');

var config = {
    target: "../../../target/classes/web",
    production: !!util.env.production
};

gulp.task('compile-lib', function () {
    var libJs = gulp.src(mainBowerFiles('**/*.js'),{ base: 'bower_components' })
        .pipe(concatBower('lib.js'))
        .pipe((config.production) ? minifyJs() : util.noop())
        .pipe(gulp.dest(config.target + '/js'));
});

gulp.task('compile-sass', function () {
    return gulp.src('themes/default/scss/*.scss')
        .pipe(sass().on('error', sass.logError))
        .pipe(gulp.dest(config.target + '/themes/default'));
});

gulp.task('copy-themes', function () {
    return gulp.src(['themes/**', '!themes/**/scss/*.scss'])
        .pipe(gulp.dest(config.target + '/themes'));
});

gulp.task('copy-jquery', function () {
    return gulp.src('jquery/**')
        .pipe(gulp.dest(config.target + "/jquery"));
});

gulp.task('copy-flash', function () {
    return gulp.src('flash/**')
        .pipe(gulp.dest(config.target + "/flash"));
});

gulp.task('copy-js', function () {
    return gulp.src('js/**')
        .pipe(gulp.dest(config.target + "/js"));
});

gulp.task('watch', function () {
    gulp.watch('themes/default/scss/*.scss', ['compile-sass']);
    gulp.watch(['themes/**', '!themes/**/scss/*.scss'], ['copy-themes']);
    gulp.watch('js/**', ['copy-js']);
});

gulp.task('default', function () {
    gulp.start('compile-sass', 'copy-themes', 'compile-lib', 'copy-js', 'copy-jquery', 'copy-flash');
});

