package it.unipi.lsmsd.gamehub.service.impl;

import it.unipi.lsmsd.gamehub.DTO.*;
import it.unipi.lsmsd.gamehub.model.Review;
import it.unipi.lsmsd.gamehub.repository.ReviewRepository;
import it.unipi.lsmsd.gamehub.service.IReviewService;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReviewService implements IReviewService {

    @Autowired
    private ReviewRepository reviewRepository;
    @Override
    public List<Review> retrieveReviewByTitle(ReviewDTO reviewDTO) {
        try {
            if(reviewDTO.getTitle()!=null){
                return reviewRepository.findByTitle(reviewDTO.getTitle());
            }
            return null;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<ReviewDTOAggregation> retrieveAggregateFirstAndLastUserLike() {
        try {
            List<ReviewDTOAggregation> reviewList= reviewRepository.findAggregation2();
            if(!reviewList.isEmpty()){
                return reviewList;
            }
            return null;
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<ReviewDTOAggregation2> findAggregation3() {
        try {
            return reviewRepository.findAggregation3();
        }catch (Exception e){
            System.out.println(e.getMessage());
            return null;
        }

    }

    @Override
    public List<Review> retrieveByTitleOrderByLikeCountDesc(ReviewDTO reviewDTO, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return reviewRepository.findByTitleOrderByLikeCountDesc(reviewDTO.getTitle(),pageable);
    }


}
