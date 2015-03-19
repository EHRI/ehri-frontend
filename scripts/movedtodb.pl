#!/usr/bin/perl

use strict;
use warnings;
use Digest::SHA qw(sha1_hex);


if (scalar(@ARGV) < 1) {
    die "Original path given!\n";
}

while (<>) {
    chomp;
    my ($from_id, $to_id) = split /\t/, $_;
    for my $path (@ARGV) {
        my $from = $path . $from_id;
        my $to = $path . $to_id;
        my $hash = sha1_hex($from);
        print "$hash\t$from\t$to\t\\N\n";
    }
}
